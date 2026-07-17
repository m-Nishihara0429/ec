package com.example.ec.repository;

import com.example.ec.entity.CartItem;
import com.example.ec.entity.Product;
import com.example.ec.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ショッピングカートの中身(CartItem)を管理するリポジトリ。
 * ユーザーごとのカート表示、商品追加時の既存行チェック、注文完了後のカート削除に使う。
 * JpaRepository<CartItem, Long> を継承することで、
 * save・findById・findAll・deleteなどの基本的なCRUD操作が自動的に使えるようになる。
 * CartItem はエンティティ型、Long は主キー(id)の型を表す。
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    // 「userカラムが一致するCartItemを全件検索する」クエリメソッド。
    // ログイン中ユーザーのカート画面に表示するカート内商品の一覧取得に使用する。
    // spring.jpa.open-in-view=false のため、カート画面・チェックアウト画面のテンプレートが
    // item.product（LAZY）を参照する時点ではセッションが閉じている。@EntityGraphでproductを
    // 同じクエリで一括取得しておくことで、テンプレート側の遅延ロードを不要にする。
    @EntityGraph(attributePaths = "product")
    List<CartItem> findByUser(User user);

    // 「userとproductの両方が一致するCartItemを1件検索する」クエリメソッド。
    // 新規追加（既存行が無い場合）の判定、および既存行はあるが上限超過で
    // incrementQuantityIfWithinLimitが0件更新だった場合の原因切り分けに使用する。
    Optional<CartItem> findByUserAndProduct(User user, Product product);

    /**
     * 既存のカート明細（user, productの組み合わせ）の数量を、上限を超えない場合のみ
     * 原子的に加算する。更新できた行数を返す（0なら「対象行が無い」または「上限超過」）。
     * ProductRepository.decreaseStockIfAvailableと同じ考え方で、「読み取り→判定→書き込み」を
     * アプリ側で別々のステップに分けると、同一商品を同時に複数回「カートに追加」した際に
     * 更新の取りこぼし（lost update）が起こりうるため、WHERE句に条件を含めたUPDATE文で
     * DB側に原子的に判定させている。
     * 既存行が無い場合（新規追加）はこのメソッドでは対応できないため、呼び出し元
     * （CartService.addToCart）で0件更新だった場合にfindByUserAndProductで存在有無を確認し、
     * 無ければ新規行としてsaveする（その新規保存同士が競合した場合は、CartItemのユニーク制約
     * uk_cart_items_user_productが最終防波堤になる）。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE CartItem c SET c.quantity = c.quantity + :delta " +
            "WHERE c.user = :user AND c.product = :product AND c.quantity + :delta <= :max")
    int incrementQuantityIfWithinLimit(@Param("user") User user, @Param("product") Product product,
                                        @Param("delta") int delta, @Param("max") int max);

    // 注文完了(チェックアウト)後にカートを空にするために使用する。
    // 「userカラムが一致するCartItemを全件削除する」クエリメソッド(戻り値なし)。
    void deleteByUser(User user);
}
