package com.example.ec.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 商品エンティティ。カテゴリに属し、カート・注文明細・レビューから参照される。
 * DB上のテーブル名は "products"。
 */
@Entity // JPAエンティティであることを示す
@Table(name = "products") // マッピング先のテーブル名を指定
@Getter // Lombok: 全フィールドのgetterを自動生成
@Setter // Lombok: 全フィールドのsetterを自動生成
@NoArgsConstructor // Lombok: 引数なしコンストラクタを自動生成
public class Product {

    @Id // 主キー
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB側の自動採番で主キーを生成
    private Long id; // 商品ID（主キー）

    @Column(nullable = false, length = 200) // NOT NULL制約、最大文字数200
    private String name; // 商品名

    @Column(columnDefinition = "TEXT") // 長文を保存できるTEXT型カラムとしてマッピング
    private String description; // 商品説明文

    @Column(nullable = false) // NOT NULL制約
    private Integer price; // 商品単価（円単位を想定）

    @Column(nullable = false) // NOT NULL制約
    private Integer stock = 0; // 在庫数。デフォルトは0

    @Column(length = 500) // 最大文字数500（NULL許容）
    private String imageUrl; // 商品画像のURLまたはパス

    // 所属カテゴリ（未分類の場合はnullを許容）
    @ManyToOne(fetch = FetchType.LAZY) // 多対一関連（多数の商品が1つのカテゴリに属する）。LAZYで実際に使うまで取得しない
    @JoinColumn(name = "category_id") // 外部キー列名を指定（NULL許容なのでカテゴリ未設定も可）
    private Category category; // 紐づくカテゴリエンティティ
}
