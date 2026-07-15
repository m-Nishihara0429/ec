package com.example.ec.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * よくある質問(FAQ)エンティティ。管理画面から登録・編集・削除する。
 * DB上のテーブル名は "faqs"。
 */
@Entity
@Table(name = "faqs")
@Getter
@Setter
@NoArgsConstructor
public class Faq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    // FAQ一覧での表示順。値が小さいものほど先に表示する
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}
