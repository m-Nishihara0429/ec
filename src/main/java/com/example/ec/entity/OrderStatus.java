package com.example.ec.entity;

/**
 * 注文のステータスを表す列挙型。PENDING（受付）→ SHIPPED（発送済み）→ COMPLETED（完了）と進むのが基本フロー。
 * COMPLETEDとCANCELLEDはそれ以上ステータスが変わらない終端状態、PENDING・SHIPPEDはCANCELLEDへ遷移しうる状態。
 */
public enum OrderStatus {
    // 注文受付直後の初期状態（キャンセル可能）
    PENDING("注文受付"),
    // 発送済み（キャンセル可能な運用も想定される中間状態）
    SHIPPED("発送済み"),
    // 配送完了などで注文が正常終了した終端状態
    COMPLETED("完了"),
    // 注文がキャンセルされた終端状態
    CANCELLED("キャンセル済み");

    private final String label; // 画面表示用の日本語ラベル文字列

    // 列挙子ごとにラベル文字列を紐づけるコンストラクタ（enumのコンストラクタは各列挙子の生成時に呼ばれる）
    OrderStatus(String label) {
        this.label = label; // 渡されたラベルをフィールドに保持
    }

    // 画面表示用の日本語ラベルを取得するgetterメソッド
    public String getLabel() {
        return label; // 保持しているラベル文字列を返す
    }
}
