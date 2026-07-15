package com.example.ec.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * パスワードリセット用トークンエンティティ。発行されたトークンと有効期限を管理する。
 * DB上のテーブル名は "password_reset_tokens"。
 */
@Entity // JPAエンティティであることを示す
@Table(name = "password_reset_tokens") // マッピング先のテーブル名を指定
@Getter // Lombok: 全フィールドのgetterを自動生成
@Setter // Lombok: 全フィールドのsetterを自動生成
@NoArgsConstructor // Lombok: 引数なしコンストラクタを自動生成
public class PasswordResetToken {

    @Id // 主キー
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB側の自動採番で主キーを生成
    private Long id; // トークンレコードのID（主キー）

    @ManyToOne(fetch = FetchType.LAZY) // 多対一関連（多数のトークンが1人のユーザーに属しうる）。LAZYで遅延取得
    @JoinColumn(name = "user_id", nullable = false) // 外部キー列名。NOT NULL制約
    private User user; // このトークンの発行対象ユーザー

    // メールで送付するランダムな一意のトークン文字列
    @Column(nullable = false, unique = true, length = 100) // NOT NULL・重複不可（ユニーク制約）、最大文字数100
    private String token; // パスワードリセット用のランダムトークン文字列

    // このトークンの有効期限。過ぎたトークンでのリセットは拒否される想定
    @Column(nullable = false) // NOT NULL制約
    private LocalDateTime expiresAt; // トークンの有効期限日時
}
