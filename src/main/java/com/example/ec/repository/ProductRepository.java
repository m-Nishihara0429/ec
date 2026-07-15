package com.example.ec.repository;

import com.example.ec.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 商品(Product)情報を管理するリポジトリ。
 * 商品検索・絞り込み(JpaSpecificationExecutor)に加え、在庫の原子的な増減操作を提供する。
 * JpaRepository<Product, Long> を継承することで、
 * save・findById・findAll・deleteなどの基本的なCRUD操作が自動的に使えるようになる。
 * さらに JpaSpecificationExecutor<Product> を継承することで、
 * Specification(検索条件をプログラムで組み立てる仕組み)を使った
 * 動的な絞り込み検索(findAll(Specification)など)も利用できるようになる。
 * これにより「カテゴリ・価格帯・キーワードなど複数条件を任意の組み合わせで検索する」
 * といった処理を、条件ごとに専用メソッドを作らずに実現できる。
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // 管理画面の「在庫少」アラート表示用。指定した閾値以下の在庫の商品を、在庫が少ない順に最大10件取得する
    // 「stockがthreshold以下(LessThanEqual)のProductを、stockの昇順(Asc)に並べ、
    // 先頭10件(Top10)だけ取得する」クエリメソッド。
    List<Product> findTop10ByStockLessThanEqualOrderByStockAsc(int threshold);

    // トップページの価格帯絞り込みスライダー(二重ハンドル)の範囲を、
    // 固定値ではなく実際のカタログデータから動的に決定するために使用する
    // @Query によるJPQLで、全商品のうち最小価格(MIN)を1件取得する。
    @Query("SELECT MIN(p.price) FROM Product p")
    Integer findMinPrice();

    // findMinPriceと同様、価格帯スライダーの上限値を実データから動的に決定するために使用する
    // @Query によるJPQLで、全商品のうち最大価格(MAX)を1件取得する。
    @Query("SELECT MAX(p.price) FROM Product p")
    Integer findMaxPrice();

    /**
     * 在庫が足りている場合のみ原子的に減算する。更新できた行数を返す(0なら在庫不足)。
     * 「在庫を読む→アプリ側で判定→書き込む」という方式だと、複数の注文が同時に
     * チェックアウトした際、どちらも古い(stale)在庫数を見て「在庫あり」と判定してしまい、
     * 在庫がマイナスになる競合状態(race condition)が起こり得る。
     * そのため WHERE句に p.stock >= :quantity を含めたUPDATE文でDB側に原子的に判定させている。
     *
     * flushAutomatically = true としているが、clearAutomatically = true は使わない。
     * clearAutomatically = true を試したところ、永続化コンテキストがクリアされてしまい、
     * OrderService.checkout() の同一トランザクション内で後続処理に使う
     * Product/CartItemエンティティがデタッチされ LazyInitializationException が発生したため、
     * 意図的に外している。
     */
    // @Modifying は、このメソッドがSELECTではなくUPDATE/DELETEを実行することをSpring Data JPAに伝える
    // アノテーション。付けないと@QueryのUPDATE文は実行時エラーになる。
    // flushAutomatically = true は、このクエリを実行する前に永続化コンテキスト(未反映の変更)を
    // 強制的にDBへ反映(flush)してから実行する設定で、更新順序のずれを防ぐ。
    @Modifying(flushAutomatically = true)
    // JPQLのUPDATE文。指定した id の商品について、現在の在庫(p.stock)から quantity を引く。
    // WHERE句に p.stock >= :quantity という条件を含めているのがポイントで、
    // 在庫が足りている場合のみDB側で一括して(原子的に)更新される。
    // @Param("id")/@Param("quantity") によりメソッド引数をJPQL内の :id / :quantity に束縛する。
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
    // 戻り値のintは実際に更新された行数。0件なら「在庫不足で減算できなかった」ことを意味し、
    // 呼び出し元(サービス層)はこれを見て在庫切れエラーを判定する。
    int decreaseStockIfAvailable(@Param("id") Long id, @Param("quantity") int quantity);

    // 注文キャンセル等での在庫戻し処理。decreaseStockIfAvailableと同様に、
    // 読み取り→書き込みではなくUPDATE文による原子的な加算で更新の取りこぼしを防ぐ
    // @Modifying でUPDATE文であることを明示し、flushAutomatically = true で
    // 実行前に未反映の変更を先にDBへ反映させる。
    @Modifying(flushAutomatically = true)
    // JPQLのUPDATE文。指定した id の商品の在庫(p.stock)に quantity を加算して戻す。
    // @Param によりメソッド引数をJPQL内の :id / :quantity に束縛する。
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.id = :id")
    // 戻り値はvoid(更新行数を呼び出し元で使わないため)。
    void increaseStockAtomic(@Param("id") Long id, @Param("quantity") int quantity);
}
