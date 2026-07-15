// このファイルが属するパッケージ宣言。エンティティ関連クラスをまとめている
package com.example.ec.entity;

/**
 * ユーザーの権限ロールを表す列挙型。
 * Spring Securityの権限判定（hasRole等）に利用される。
 * 列挙値の名前は "ROLE_" プレフィックスを持つ必要があるSpring Securityの慣例に従っている。
 */
public enum Role {
    /** 一般ユーザー（通常の購入者）。商品閲覧・購入・レビュー投稿などが可能 */
    ROLE_USER("一般ユーザー"),
    /** 管理者（商品・注文管理などの操作が可能） */
    ROLE_ADMIN("管理者"),
    /** マスター管理者（管理者の権限に加え、会員のロール変更・アカウント有効/無効化などの会員管理が可能） */
    ROLE_MASTER("マスター管理者");

    // 画面表示用の日本語ラベル文字列
    private final String label;

    // 列挙子ごとにラベル文字列を紐づけるコンストラクタ（enumのコンストラクタは各列挙子の生成時に呼ばれる）
    Role(String label) {
        this.label = label;
    }

    // 画面表示用の日本語ラベルを取得するgetterメソッド
    public String getLabel() {
        return label;
    }
}
