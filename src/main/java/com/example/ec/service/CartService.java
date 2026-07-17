package com.example.ec.service;

import com.example.ec.entity.CartItem;
import com.example.ec.entity.Product;
import com.example.ec.entity.User;
import com.example.ec.repository.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ショッピングカートの商品追加・数量変更・削除・合計金額計算を行うサービスクラス。
 * カートの中身（CartItem）はユーザーごとに管理され、注文（OrderService.checkout）で
 * 実際の注文に変換されるまでの「一時的な買い物かご」の役割を持つ。
 */
@Service // このクラスをSpringのサービス層Beanとして登録する
public class CartService {

    // 1商品あたりカートに入れられる数量の上限。上限が無いと、負数や極端に大きい数量を
    // 送りつけることで在庫の原子的減算(stock >= quantity)や注文合計金額の計算が破綻する
    // （負数なら在庫が減るはずが増える、大きすぎる数量ならint演算がオーバーフローしうる）
    private static final int MAX_QUANTITY_PER_ITEM = 99;

    // カート明細（CartItem）のデータアクセスを担当するリポジトリ
    private final CartItemRepository cartItemRepository;

    // コンストラクタインジェクションでリポジトリを受け取る
    public CartService(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    /**
     * 指定したユーザーのカート内商品一覧を取得する。
     *
     * @param user 対象ユーザー
     * @return そのユーザーのカート明細一覧（空の場合は空リスト）
     */
    public List<CartItem> findByUser(User user) {
        // ユーザーに紐づくカート明細をリポジトリから取得してそのまま返す
        return cartItemRepository.findByUser(user);
    }

    /**
     * カートに商品を追加する。
     * 既に同じ商品がカートにある場合は数量を加算し、なければ新規にカート明細を作成する。
     *
     * <p>既存行への加算は、ProductService.decreaseStockと同じ考え方で
     * {@link CartItemRepository#incrementQuantityIfWithinLimit}による原子的なUPDATEで行う。
     * 「読み取り→アプリ側で加算後の値を計算→書き込み」という方式だと、同じ商品を同時に
     * 複数回「カートに追加」した際に、どちらも古い（stale）数量を見て加算してしまい、
     * 片方の加算が失われる競合状態（lost update）が起こり得るため。
     * 新規追加（対象行がまだ無い）の場合はこのUPDATEでは対応できないため、0件更新だった場合に
     * 既存行の有無を確認し、無ければ新規行としてINSERTする。その新規INSERT同士が競合した場合は
     * CartItemのユニーク制約（uk_cart_items_user_product）が最終防波堤になり、
     * DataIntegrityViolationExceptionとしてCartControllerまで伝播する。</p>
     *
     * @param user     カートの持ち主
     * @param product  追加する商品
     * @param quantity 追加する数量
     * @throws IllegalArgumentException 追加する数量が1未満、上限（{@value #MAX_QUANTITY_PER_ITEM}）超過、
     *                                   または加算後の数量が上限を超える場合
     */
    @Transactional // 原子的UPDATE・存在確認・新規保存を1つの整合した処理としてまとめる
    public void addToCart(User user, Product product, int quantity) {
        // 数量が1未満（0や負数）の場合、在庫の原子的減算や注文合計金額の計算が破綻するため拒否する
        if (quantity < 1) {
            throw new IllegalArgumentException("数量は1個以上を指定してください");
        }
        // 新規追加の場合に備え、単独の数量自体が上限を超えていないかも先にチェックする
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new IllegalArgumentException("1つの商品につきカートに入れられる数量は" + MAX_QUANTITY_PER_ITEM + "個までです");
        }
        // 既存行があり、かつ加算後も上限以内であれば、この1本のUPDATEで原子的に加算が完了する
        int updated = cartItemRepository.incrementQuantityIfWithinLimit(user, product, quantity, MAX_QUANTITY_PER_ITEM);
        if (updated > 0) {
            return;
        }
        // 0件更新の場合、「既存行はあるが上限超過」か「そもそも既存行が無い（新規追加）」かを切り分ける
        if (cartItemRepository.findByUserAndProduct(user, product).isPresent()) {
            throw new IllegalArgumentException("1つの商品につきカートに入れられる数量は" + MAX_QUANTITY_PER_ITEM + "個までです");
        }
        // 既存行が無い場合は新規のカート明細としてINSERTする
        cartItemRepository.save(new CartItem(user, product, quantity));
    }

    /**
     * 指定したカート明細の数量を、加算ではなく指定値そのものに置き換える。
     * 他ユーザーのカート明細を操作できないよう、所有者チェックを行う。
     *
     * @param cartItemId 更新対象のカート明細ID
     * @param quantity   設定したい数量
     * @param user       操作を要求しているユーザー（ログイン中の本人）
     * @throws IllegalArgumentException 指定したIDのカート明細が存在しない、他人のカート明細である場合、
     *                                   または数量が1未満・上限（{@value #MAX_QUANTITY_PER_ITEM}）超過の場合
     */
    @Transactional // 検索と更新をまとめて1トランザクションにする
    public void updateQuantity(Long cartItemId, int quantity, User user) {
        // 数量が範囲外（1未満または上限超過）の場合は拒否する（addToCartと同じ理由）
        if (quantity < 1 || quantity > MAX_QUANTITY_PER_ITEM) {
            throw new IllegalArgumentException("数量は1〜" + MAX_QUANTITY_PER_ITEM + "個の範囲で指定してください");
        }
        // IDでカート明細を検索し、存在しなければ例外を投げる
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("カート商品が見つかりません: " + cartItemId));
        // 他人のカート明細の場合は、存在自体を伏せるため「見つからない」として扱う
        // （OrderService.cancelByUserと同様、注文/カートIDの存在を手がかりにした情報漏えいを防ぐ）
        if (!item.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("カート商品が見つかりません: " + cartItemId);
        }
        // 数量を指定された値に置き換える（addToCartと違い加算ではなく上書き）
        item.setQuantity(quantity);
        // 変更を保存する
        cartItemRepository.save(item);
    }

    /**
     * カート明細を1件削除する。他ユーザーのカート明細を操作できないよう、所有者チェックを行う。
     *
     * @param cartItemId 削除対象のカート明細ID
     * @param user       操作を要求しているユーザー（ログイン中の本人）
     * @throws IllegalArgumentException 指定したIDのカート明細が存在しない、または他人のカート明細である場合
     */
    @Transactional // 検索と削除をまとめて1トランザクションにする
    public void removeItem(Long cartItemId, User user) {
        // IDでカート明細を検索し、存在しなければ例外を投げる
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("カート商品が見つかりません: " + cartItemId));
        // 他人のカート明細の場合は、存在自体を伏せるため「見つからない」として扱う
        if (!item.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("カート商品が見つかりません: " + cartItemId);
        }
        // 所有者チェックを通過したので削除する
        cartItemRepository.delete(item);
    }

    /**
     * 指定ユーザーのカートを空にする。注文（checkout）完了後の後始末として使われる。
     *
     * @param user カートを空にする対象ユーザー
     */
    @Transactional // ユーザーの全カート明細をまとめて削除するのでトランザクション化する
    public void clear(User user) {
        // そのユーザーに紐づくカート明細を一括削除する
        cartItemRepository.deleteByUser(user);
    }

    /**
     * カート内商品の合計金額を計算する。
     *
     * @param user 対象ユーザー
     * @return カート内全商品の小計（単価×数量）を合計した金額
     */
    public int totalPrice(User user) {
        // ユーザーのカート明細を取得し、各明細の小計（subtotal＝単価×数量）を合算する
        return findByUser(user).stream().mapToInt(CartItem::subtotal).sum();
    }
}
