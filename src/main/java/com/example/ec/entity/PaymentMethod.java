package com.example.ec.entity;

/**
 * 注文の支払い方法を表す列挙型。
 * 本サイトは学習用のデモサイトのため実際の決済処理は行わず、選択された方法を記録するのみ。
 */
public enum PaymentMethod {
    // クレジットカード（デモのため実際の課金は発生しない）
    CREDIT_CARD("クレジットカード"),
    // 代金引換
    COD("代金引換"),
    // 銀行振込
    BANK_TRANSFER("銀行振込"),
    // コンビニ払い
    CONVENIENCE_STORE("コンビニ払い");

    private final String label; // 画面表示用の日本語ラベル文字列

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
