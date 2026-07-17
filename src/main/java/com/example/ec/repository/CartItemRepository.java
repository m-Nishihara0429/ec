package com.example.ec.repository;

import com.example.ec.entity.CartItem;
import com.example.ec.entity.Product;
import com.example.ec.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
    // 同じ商品を再度カートに追加しようとした際に、新規行を作らず既存行の数量を
    // 増やすべきかどうかを判定するために使用する。存在しなければOptional.empty()。
    Optional<CartItem> findByUserAndProduct(User user, Product product);

    // 注文完了(チェックアウト)後にカートを空にするために使用する。
    // 「userカラムが一致するCartItemを全件削除する」クエリメソッド(戻り値なし)。
    void deleteByUser(User user);
}
