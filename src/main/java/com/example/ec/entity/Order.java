package com.example.ec.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 注文エンティティ。ユーザー・配送先住所・複数の注文明細（OrderItem）をまとめて保持する。
 * DB上のテーブル名は "orders"。
 */
@Entity // JPAエンティティであることを示す
@Table(name = "orders") // マッピング先のテーブル名を指定
@Getter // Lombok: 全フィールドのgetterを自動生成
@Setter // Lombok: 全フィールドのsetterを自動生成
@NoArgsConstructor // Lombok: 引数なしコンストラクタを自動生成
public class Order {

    @Id // 主キー
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB側の自動採番で主キーを生成
    private Long id; // 注文ID（主キー）

    @ManyToOne(fetch = FetchType.LAZY) // 多対一関連（多数の注文が1人のユーザーに属する）。LAZYで遅延取得
    @JoinColumn(name = "user_id", nullable = false) // 外部キー列名。NOT NULL制約
    private User user; // 注文したユーザー

    // status列にはSQLiteのCHECK制約があり、CANCELLED追加時は手動でDBスキーマを移行する必要があった
    // （ddl-auto=updateはSQLiteのCHECK制約を変更しないため）。
    @Enumerated(EnumType.STRING) // 列挙型を文字列（列挙子の名前）としてDBに保存する指定
    @Column(nullable = false, length = 20) // NOT NULL制約、最大文字数20
    private OrderStatus status = OrderStatus.PENDING; // 注文ステータス。デフォルトは受付状態

    @Column(nullable = false) // NOT NULL制約
    private Integer totalPrice; // 注文の合計金額

    @Column(nullable = false, length = 500) // NOT NULL制約、最大文字数500
    private String address; // 配送先住所

    // 支払い方法。既存の"orders"テーブルにNOT NULL列を追加するALTER TABLEをSQLiteで安全に行うため、
    // ColumnDefaultでDEFAULT句を生成させる（statusのCHECK制約で苦労した教訓を踏まえた対策）
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'COD'")
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod = PaymentMethod.COD; // 支払い方法。デフォルトは代金引換

    @Column(length = 30)
    private String couponCode; // 適用したクーポンのコード（未使用ならnull）

    @ColumnDefault("0")
    @Column(nullable = false)
    private Integer discountAmount = 0; // クーポンによる割引額（未使用なら0）

    @Column(nullable = false) // NOT NULL制約
    private LocalDateTime createdAt = LocalDateTime.now(); // 注文日時。インスタンス生成時点の現在時刻で初期化される

    // 注文に含まれる明細一覧。Order削除時は明細も連鎖削除される（cascade + orphanRemoval）
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    // mappedBy = "order": OrderItem側のorderフィールドが関連の所有者であることを示す（外部キーはOrderItem側にある）
    // cascade = CascadeType.ALL: Orderへの永続化・削除操作をOrderItemにも連鎖させる
    // orphanRemoval = true: itemsリストから取り除かれたOrderItemはDBからも自動削除される
    // fetch = FetchType.LAZY: 明細一覧は必要になるまで取得しない
    private List<OrderItem> items = new ArrayList<>(); // 注文明細のリスト。初期値は空のArrayList

    // 双方向関連を維持しつつ明細を追加するヘルパーメソッド
    public void addItem(OrderItem item) {
        item.setOrder(this); // 明細側からもこの注文を参照できるように設定（双方向関連の整合性を保つ）
        items.add(item); // 明細一覧にこの明細を追加
    }
}
