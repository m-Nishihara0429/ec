package com.example.ec.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 商品カテゴリエンティティ。商品を分類するために使用される。
 * DB上のテーブル名は "categories"。
 */
@Entity // JPAエンティティ（DBテーブルにマッピングされるクラス）であることを示す
@Table(name = "categories") // マッピング先のテーブル名を指定する
@Getter // Lombok: 全フィールドのgetterを自動生成
@Setter // Lombok: 全フィールドのsetterを自動生成
@NoArgsConstructor // Lombok: 引数なしコンストラクタを自動生成（JPAが必要とする）
public class Category {

    @Id // 主キー
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB側の自動採番で主キーを生成
    private Long id; // カテゴリID（主キー）

    @Column(nullable = false, length = 100) // NOT NULL制約、最大文字数100
    private String name; // カテゴリ名（例: 「書籍」「食品」など）

    // name を指定してカテゴリを生成するための引数ありコンストラクタ
    public Category(String name) {
        this.name = name; // 渡されたカテゴリ名をフィールドに設定
    }
}
