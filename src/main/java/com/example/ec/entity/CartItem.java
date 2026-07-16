package com.example.ec.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * カート内商品エンティティ。ユーザーと商品を紐づけ、購入前の一時的な数量を保持する。
 * DB上のテーブル名は "cart_items"。
 */
// (user_id, product_id) にユニーク制約を付け、同一ユーザー・同一商品のカート明細が
// 2行に分かれないようDBレベルでも保証する。CartService.addToCartは「検索→加算or新規作成」の
// check-then-actであり、ほぼ同時に2回「カートに追加」を送信された場合にこの間で競合しうるため、
// この制約がその競合時の最終防波堤になる（Orderのクーポン重複防止と同じ考え方）
@Entity // JPAエンティティであることを示す
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(name = "uk_cart_items_user_product", columnNames = {"user_id", "product_id"})) // マッピング先のテーブル名を指定
@Getter // Lombok: 全フィールドのgetterを自動生成
@Setter // Lombok: 全フィールドのsetterを自動生成
@NoArgsConstructor // Lombok: 引数なしコンストラクタを自動生成
public class CartItem {

    @Id // 主キー
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB側の自動採番で主キーを生成
    private Long id; // カート明細ID（主キー）

    @ManyToOne(fetch = FetchType.LAZY) // 多対一関連（多数のカート明細が1人のユーザーに属する）。LAZYで遅延取得
    @JoinColumn(name = "user_id", nullable = false) // 外部キー列名。NOT NULL制約
    private User user; // このカート明細を持つユーザー

    @ManyToOne(fetch = FetchType.LAZY) // 多対一関連（多数のカート明細が1つの商品を指しうる）。LAZYで遅延取得
    @JoinColumn(name = "product_id", nullable = false) // 外部キー列名。NOT NULL制約
    private Product product; // カートに入れられた商品

    @Column(nullable = false) // NOT NULL制約
    private Integer quantity; // カートに入れた数量

    // user, product, quantity を指定してカート明細を生成するコンストラクタ
    public CartItem(User user, Product product, Integer quantity) {
        this.user = user; // ユーザーを設定
        this.product = product; // 商品を設定
        this.quantity = quantity; // 数量を設定
    }

    // 商品単価 × 数量で小計を算出する
    public int subtotal() {
        return product.getPrice() * quantity; // 商品の現在価格に数量を掛けて小計を計算して返す
    }
}
