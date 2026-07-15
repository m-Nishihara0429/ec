package com.example.ec.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商品レビューエンティティ。1ユーザーにつき1商品に1件までレビュー可能（product_id, user_idの複合ユニーク制約）。
 * DB上のテーブル名は "reviews"。
 */
@Entity // JPAエンティティであることを示す
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "user_id"}))
// ↑ テーブル名の指定に加え、product_id・user_idの組み合わせに対する複合ユニーク制約（同じ組み合わせは1行のみ）を指定
@Getter // Lombok: 全フィールドのgetterを自動生成
@Setter // Lombok: 全フィールドのsetterを自動生成
@NoArgsConstructor // Lombok: 引数なしコンストラクタを自動生成
public class Review {

    @Id // 主キー
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB側の自動採番で主キーを生成
    private Long id; // レビューID（主キー）

    @ManyToOne(fetch = FetchType.LAZY) // 多対一関連（多数のレビューが1つの商品に属する）。LAZYで遅延取得
    @JoinColumn(name = "product_id", nullable = false) // 外部キー列名。NOT NULL制約
    private Product product; // レビュー対象の商品

    @ManyToOne(fetch = FetchType.LAZY) // 多対一関連（多数のレビューが1人のユーザーに属する）。LAZYで遅延取得
    @JoinColumn(name = "user_id", nullable = false) // 外部キー列名。NOT NULL制約
    private User user; // レビューを投稿したユーザー

    // 評価点数（1〜5などの範囲を想定。バリデーションは呼び出し側で行う）
    @Column(nullable = false) // NOT NULL制約
    private Integer rating; // 評価点数

    @Column(columnDefinition = "TEXT") // 長文を保存できるTEXT型カラム（NULL許容）
    private String comment; // レビュー本文コメント

    @Column(nullable = false) // NOT NULL制約
    private LocalDateTime createdAt = LocalDateTime.now(); // レビュー投稿日時。インスタンス生成時点の現在時刻で初期化される
}
