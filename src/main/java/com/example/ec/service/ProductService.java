package com.example.ec.service;

import com.example.ec.dto.ProductForm;
import com.example.ec.dto.ProductSort;
import com.example.ec.entity.Category;
import com.example.ec.entity.Product;
import com.example.ec.repository.CategoryRepository;
import com.example.ec.repository.ProductRepository;
import com.example.ec.specification.ProductSpecifications;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 商品の検索・参照・登録・在庫増減を行うサービスクラス。
 *
 * <p>特に重要なのは在庫増減（decreaseStock / increaseStock）で、複数ユーザーが
 * 同時に同じ商品を注文しても在庫数が不整合（マイナスなど）にならないよう、
 * 「在庫チェック＋更新」を1本のSQL（UPDATE ... WHERE stock >= ?）で原子的に行っている。
 * これはアプリケーション側で「読み取り→判定→書き込み」を別々に行うと、複数リクエストが
 * 同時に「在庫あり」と判定してしまい在庫がマイナスになる競合状態（レースコンディション）を
 * 起こしうるため、それをDBのUPDATE文自体の原子性（atomicity）に任せて防ぐ設計になっている。</p>
 */
@Service // Springのサービス層Beanとして登録する
public class ProductService {

    // 商品のデータアクセスを担当するリポジトリ
    private final ProductRepository productRepository;
    // カテゴリのデータアクセスを担当するリポジトリ（商品保存時のカテゴリ紐付けに使用）
    private final CategoryRepository categoryRepository;
    // 平均評価の取得に使うレビューサービス（評価順ソートに使用）
    private final ReviewService reviewService;

    // コンストラクタインジェクションで必要な依存を受け取る
    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                           ReviewService reviewService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.reviewService = reviewService;
    }

    /**
     * 条件（カテゴリ・キーワード・価格帯）とソート順を指定して商品をページング検索する。
     * カテゴリ・キーワード・価格帯の条件をSpecificationで組み立ててDBクエリとして実行する。
     * キーワード検索（ProductSpecifications.keywordContains）側で表記ゆれ・同義語対応が
     * 行われることを前提に、ここではキーワード文字列をそのまま条件組み立てに渡すだけになっている。
     * 評価順（RATING_DESC）は商品テーブルのカラムではなくレビューの集計値であり、DBクエリの
     * ORDER BYで直接ソートできないため、専用メソッド（searchSortedByRating）でアプリ側ソートを行う。
     *
     * @param categoryId 絞り込みたいカテゴリID（nullなら絞り込みなし）
     * @param keyword    検索キーワード（nullや空文字なら絞り込みなし。表記ゆれ・同義語対応はSpecification側で行う）
     * @param minPrice   価格帯の下限（nullなら下限なし）
     * @param maxPrice   価格帯の上限（nullなら上限なし）
     * @param sort       ソート順（nullの場合はNEWEST＝新着順を既定値として使う）
     * @param pageable   ページ番号・1ページあたりの件数などのページング情報
     * @return 条件に合致する商品のページ
     */
    // カテゴリ・キーワード・価格帯の条件をSpecificationで組み立てて検索する。
    // 評価順（RATING_DESC）はDBのカラムではないため、専用のメソッドでアプリ側ソートを行う
    public Page<Product> search(Long categoryId, String keyword, Integer minPrice, Integer maxPrice,
                                 ProductSort sort, Pageable pageable) {
        // 複数の検索条件（カテゴリ一致・キーワード部分一致・価格下限・価格上限）をAND結合して1つの検索条件を組み立てる
        // keywordContainsの中で表記ゆれ（全角/半角・ひらがな/カタカナ等）や同義語を考慮した条件が組み立てられる
        Specification<Product> spec = Specification
                .where(ProductSpecifications.categoryEquals(categoryId))
                .and(ProductSpecifications.keywordContains(keyword))
                .and(ProductSpecifications.priceGreaterThanOrEqual(minPrice))
                .and(ProductSpecifications.priceLessThanOrEqual(maxPrice));

        // ソート指定が無い（null）場合は、既定として新着順（NEWEST）を使う
        ProductSort effectiveSort = sort == null ? ProductSort.NEWEST : sort;

        // 評価順が指定された場合は、DBのORDER BYでは表現できないためアプリ側でソートする専用処理に分岐する
        if (effectiveSort == ProductSort.RATING_DESC) {
            return searchSortedByRating(spec, pageable);
        }

        // 評価順以外のソート種別を、実際のJPA用ソート指定（Sort）に変換する
        Sort jpaSort = switch (effectiveSort) {
            // 価格の安い順
            case PRICE_ASC -> Sort.by("price").ascending();
            // 価格の高い順
            case PRICE_DESC -> Sort.by("price").descending();
            // それ以外（NEWESTなど）はIDの降順＝新しく登録された商品ほど先頭にくる並び
            default -> Sort.by("id").descending();
        };
        // 元のページング情報（ページ番号・件数）にソート条件を追加した新しいPageableを作る
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), jpaSort);
        // 条件とソート・ページングを指定してDBから検索結果を取得する
        return productRepository.findAll(spec, sortedPageable);
    }

    /**
     * 平均評価の高い順に商品を検索する（searchメソッドの内部専用処理）。
     * 平均評価はレビューテーブル側の集計値でありDBクエリで直接ソートできないため、
     * 条件に一致する商品を一旦全件取得したうえで、メモリ上で評価順に並べ替え、
     * 手動でページング（部分リストの切り出し）を行う。
     * 商品件数が非常に多い場合は全件取得のコストが大きくなる点に注意（学習用途の規模を想定した実装）。
     *
     * @param spec     カテゴリ・キーワード・価格帯などの検索条件
     * @param pageable ページ番号・1ページあたりの件数
     * @return 平均評価の高い順に並べ替えた商品のページ
     */
    // 平均評価はレビューテーブルの集計値でありDBクエリで直接ソートできないため、
    // 条件に一致する商品を全件取得してからメモリ上で評価順に並べ替え、手動でページングする
    private Page<Product> searchSortedByRating(Specification<Product> spec, Pageable pageable) {
        // 検索条件に合致する商品を、ページングせず全件取得する
        List<Product> matched = productRepository.findAll(spec);
        // 取得した商品のID一覧を渡し、商品ID→平均評価のマップを1回のクエリでまとめて取得する（N+1問題を回避）
        Map<Long, Double> ratings = reviewService.averageRatingsFor(matched.stream().map(Product::getId).toList());
        // 各商品を平均評価（無ければ0.0扱い）で比較し、降順（評価の高い順）に並べ替える
        List<Product> sorted = matched.stream()
                .sorted(Comparator.comparingDouble((Product p) -> ratings.getOrDefault(p.getId(), 0.0)).reversed())
                .toList();

        // 何件目から切り出すか（ページの開始位置）を計算する
        int start = (int) pageable.getOffset();
        // 開始位置が全件数を超えている（存在しないページを要求された）場合は空のページを返す
        if (start >= sorted.size()) {
            return new PageImpl<>(List.of(), pageable, sorted.size());
        }
        // 終了位置は「開始位置＋1ページあたりの件数」と「全件数」のうち小さい方（範囲外アクセス防止）
        int end = Math.min(start + pageable.getPageSize(), sorted.size());
        // 並べ替え済みリストから該当範囲を切り出し、元のページング情報・全体件数と合わせてPageオブジェクトにする
        return new PageImpl<>(sorted.subList(start, end), pageable, sorted.size());
    }

    /**
     * 商品をIDで1件取得する。
     *
     * @param id 取得したい商品のID
     * @return 該当する商品
     * @throws IllegalArgumentException 指定したIDの商品が存在しない場合
     */
    public Product findById(Long id) {
        // IDで商品を検索し、存在しなければ例外を投げる
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("商品が見つかりません: " + id));
    }

    /**
     * 登録済み商品の総数を取得する（管理画面のダッシュボード表示向け）。
     *
     * @return 商品の総数
     */
    public long count() {
        // リポジトリのcountをそのまま呼び出す
        return productRepository.count();
    }

    /**
     * 全商品の中での最安値を取得する（検索画面の価格帯フィルタの初期値表示などに使用）。
     *
     * @return 最安値（商品が1件も無い場合は0）
     */
    public int findMinPrice() {
        // DB側で最小価格を集計するクエリを実行する
        Integer min = productRepository.findMinPrice();
        // 商品が1件も無い場合はnullが返るため、その場合は0として扱う
        return min == null ? 0 : min;
    }

    /**
     * 全商品の中での最高値を取得する（検索画面の価格帯フィルタの初期値表示などに使用）。
     *
     * @return 最高値（商品が1件も無い場合は0）
     */
    public int findMaxPrice() {
        // DB側で最大価格を集計するクエリを実行する
        Integer max = productRepository.findMaxPrice();
        // 商品が1件も無い場合はnullが返るため、その場合は0として扱う
        return max == null ? 0 : max;
    }

    /**
     * 在庫が少ない商品を、在庫が少ない順に最大10件取得する（管理画面の在庫アラート表示向け）。
     *
     * @param threshold 「在庫僅少」とみなす在庫数の閾値（この値以下の商品が対象）
     * @return 在庫僅少商品の一覧（最大10件、在庫数の昇順）
     */
    public List<Product> findLowStock(int threshold) {
        // 在庫数が閾値以下の商品を、在庫数の少ない順に先頭10件だけ取得する
        return productRepository.findTop10ByStockLessThanEqualOrderByStockAsc(threshold);
    }

    /**
     * 商品を保存する（登録・編集共通の処理）。
     * フォームにIDが設定されていれば既存商品を検索して更新し、IDが未設定（null）であれば
     * 新規商品として作成する。
     *
     * @param form 商品の入力内容（名前・説明・価格・在庫・画像URL・カテゴリIDなど）
     * @return 保存後の商品エンティティ
     * @throws IllegalArgumentException 指定したカテゴリIDが存在しない場合、
     *                                   または更新対象の商品IDが存在しない場合（findById経由）
     */
    // フォームにIDがあれば既存商品を更新、なければ新規商品として作成する（登録・編集共通の処理）
    @Transactional // カテゴリ検索を含む一連の更新処理を1つの整合した処理としてまとめる
    public Product save(ProductForm form) {
        // フォームにIDがあれば既存商品を取得（更新）、無ければ新規Productインスタンスを作る（新規登録）
        Product product = form.getId() != null ? findById(form.getId()) : new Product();
        // 商品名をフォームの値で設定する
        product.setName(form.getName());
        // 商品説明をフォームの値で設定する
        product.setDescription(form.getDescription());
        // 価格をフォームの値で設定する
        product.setPrice(form.getPrice());
        // 在庫数をフォームの値で設定する
        product.setStock(form.getStock());
        // 商品画像URLをフォームの値で設定する
        product.setImageUrl(form.getImageUrl());
        // カテゴリIDが指定されている場合の処理
        if (form.getCategoryId() != null) {
            // 指定されたカテゴリIDでカテゴリを検索し、存在しなければ例外を投げる
            Category category = categoryRepository.findById(form.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("カテゴリが見つかりません: " + form.getCategoryId()));
            // 見つかったカテゴリを商品に設定する
            product.setCategory(category);
        } else {
            // カテゴリIDが指定されていない場合は、商品のカテゴリを未設定（null）にする
            product.setCategory(null);
        }
        // 商品を保存し、保存後のエンティティを返す（新規ならINSERT、既存ならUPDATE）
        return productRepository.save(product);
    }

    /**
     * 商品をIDで削除する。
     *
     * @param id 削除対象の商品ID
     */
    public void deleteById(Long id) {
        // 指定IDの商品を削除する
        productRepository.deleteById(id);
    }

    /**
     * 在庫を減算する（注文確定時などに使用）。
     * 「在庫が十分にあるか確認する」処理と「実際に減算する」処理を1つのUPDATE文
     * （decreaseStockIfAvailable、実装はProductRepository側で
     * "UPDATE ... SET stock = stock - ? WHERE id = ? AND stock >= ?" のような形になっている想定）で
     * 原子的に行うことで、複数ユーザーが同時に同じ商品を注文しても在庫がマイナスになることを防ぐ。
     * もしアプリ側で「在庫を読み取る→在庫があるか判定する→減算して保存する」と別々のステップに
     * 分けてしまうと、2人のユーザーがほぼ同時に「在庫あり」と判定してしまい、実際の在庫数以上の
     * 注文を通してしまう競合状態（レースコンディション）が起こりうる。それをこの1本のSQL文に
     * まとめることで防いでいる。
     *
     * @param product  在庫を減らす対象商品
     * @param quantity 減らす数量
     * @throws IllegalStateException 在庫が不足しており減算できなかった場合
     */
    // 在庫チェックと減算を1つのUPDATE文で原子的に行い、同時注文による在庫のマイナス超過を防ぐ。
    // 商品名はこのUPDATEの前に取得しておく：更新後にproductの遅延ロードされたプロパティへ
    // アクセスするのは不安定なため、失敗時のエラーメッセージ用に先に安全な値として確保している
    @Transactional // 原子的UPDATEを含む処理をトランザクションとして扱う
    public void decreaseStock(Product product, int quantity) {
        // エラーメッセージ用に商品名を先に取得しておく（UPDATE後にproductの遅延ロードプロパティへ
        // アクセスするのは、セッションの状態次第で不安定になりうるため、安全な値として先に確保する）
        String productName = product.getName();
        // 「在庫が指定数量以上あるか」の条件付きで在庫を減算するUPDATEを実行する。
        // 条件を満たしDBの行が更新されれば1（またはそれ以上）、在庫不足で更新できなければ0が返る
        int updated = productRepository.decreaseStockIfAvailable(product.getId(), quantity);
        // 更新件数が0＝在庫不足で減算できなかった場合は例外を投げてチェックアウト処理を失敗させる
        if (updated == 0) {
            throw new IllegalStateException("在庫が不足しています: " + productName);
        }
    }

    /**
     * 在庫を増やす（注文キャンセル時に在庫を元に戻す場合などに使用）。
     * こちらもincreaseStockAtomicという原子的なUPDATE文で加算するため、他の在庫操作と
     * 同時に実行されても在庫数の整合性が崩れない。
     *
     * @param product  在庫を増やす対象商品
     * @param quantity 増やす数量
     */
    // 注文キャンセル時などに在庫を戻すための原子的なUPDATE
    @Transactional // 原子的UPDATEを含む処理をトランザクションとして扱う
    public void increaseStock(Product product, int quantity) {
        // 「在庫を指定数量だけ加算する」UPDATEを原子的に実行する（減算と違い上限チェックは不要なため単純な加算）
        productRepository.increaseStockAtomic(product.getId(), quantity);
    }
}
