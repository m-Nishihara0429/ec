package com.example.ec.entity;

/**
 * クーポンの割引方式を表す列挙型。
 */
public enum DiscountType {
    // 注文合計金額に対する割引率（%）
    PERCENTAGE("割引率(%)"),
    // 固定の割引額（円）
    FIXED_AMOUNT("固定額(円)");

    private final String label; // 画面表示用の日本語ラベル文字列

    DiscountType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
