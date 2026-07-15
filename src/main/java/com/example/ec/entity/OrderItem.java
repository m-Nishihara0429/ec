package com.example.ec.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 注文明細エンティティ。注文時点の商品・数量・価格のスナップショットを保持する。
 * DB上のテーブル名は "order_items"。
 */
@Entity // JPAエンティティであることを示す
@Table(name = "order_items") // マッピング先のテーブル名を指定
@Getter // Lombok: 全フィールドのgetterを自動生成
@Setter // Lombok: 全フィールドのsetterを自動生成
@NoArgsConstructor // Lombok: 引数なしコンストラクタを自動生成
public class OrderItem {

    @Id // 主キー
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB側の自動採番で主キーを生成
    private Long id; // 注文明細ID（主キー）

    @ManyToOne(fetch = FetchType.LAZY) // 多対一関連（多数の明細が1つの注文に属する）。LAZYで遅延取得
    @JoinColumn(name = "order_id", nullable = false) // 外部キー列名。NOT NULL制約
    private Order order; // この明細が属する注文

    @ManyToOne(fetch = FetchType.LAZY) // 多対一関連（多数の明細が1つの商品を指しうる）。LAZYで遅延取得
    @JoinColumn(name = "product_id", nullable = false) // 外部キー列名。NOT NULL制約
    private Product product; // 注文された商品

    @Column(nullable = false) // NOT NULL制約
    private Integer quantity; // 注文数量

    // 注文当時の単価（商品の価格が後から変更されても注文内容は変わらないよう保持）
    @Column(nullable = false) // NOT NULL制約
    private Integer price; // 注文時点での商品単価のスナップショット

    // product, quantity, price を指定して注文明細を生成するコンストラクタ
    public OrderItem(Product product, Integer quantity, Integer price) {
        this.product = product; // 商品を設定
        this.quantity = quantity; // 数量を設定
        this.price = price; // 注文当時の単価を設定
    }

    // 注文当時の単価 × 数量で小計を算出する
    public int subtotal() {
        return price * quantity; // 保存済みの単価に数量を掛けて小計を計算して返す
    }
}
