package com.example.ec.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 会員ユーザーエンティティ。ログイン認証情報を持ち、注文・カート・レビュー・パスワードリセットから参照される。
 * DB上のテーブル名は "users"。
 */
@Entity // このクラスがJPAエンティティ（DBテーブルとマッピングされるクラス）であることを示すアノテーション
@Table(name = "users") // マッピング先のテーブル名を明示的に指定する
@Getter // Lombok: 全フィールドのgetterメソッドをコンパイル時に自動生成する
@Setter // Lombok: 全フィールドのsetterメソッドをコンパイル時に自動生成する
@NoArgsConstructor // Lombok: 引数なしコンストラクタを自動生成する（JPAがインスタンス生成時に必要とする）
public class User {

    @Id // 主キー（テーブルの一意識別列）であることを示す
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 主キーの値をDB側の自動採番（AUTO_INCREMENT相当）に任せる
    private Long id; // ユーザーID（主キー）

    @Column(nullable = false, length = 100) // NOT NULL制約、最大文字数100の列としてマッピング
    private String name; // ユーザーの表示名

    @Column(nullable = false, unique = true, length = 255) // NOT NULL・重複不可（ユニーク制約）の列
    private String email; // ログインIDとしても使われるメールアドレス

    // ハッシュ化済みパスワード（平文は保存しない）
    @Column(nullable = false, length = 255) // NOT NULL制約付きの列
    private String password; // BCryptなどでハッシュ化されたパスワード文字列

    @Enumerated(EnumType.STRING) // 列挙型を（序数ではなく）文字列（列挙子の名前）としてDBに保存する指定
    @Column(nullable = false, length = 20) // NOT NULL制約、最大文字数20の列
    private Role role = Role.ROLE_USER; // ユーザーの権限ロール。デフォルトは一般ユーザー

    // アカウントが有効かどうか（マスター管理者が会員を無効化＝ログイン不可にできる）。
    // @ColumnDefault("true")により、既存データが入ったテーブルにこの列を追加するALTER TABLEでも
    // 「未指定時はtrue」というDEFAULT句が生成されるため、SQLiteでも安全にマイグレーションできる。
    @ColumnDefault("true")
    @Column(nullable = false) // NOT NULL制約付きの列
    private boolean enabled = true; // 無効化（アカウント停止）されていなければtrue

    @Column(nullable = false) // NOT NULL制約付きの列
    private LocalDateTime createdAt = LocalDateTime.now(); // レコード作成日時。インスタンス生成時点の現在時刻で初期化される
}
