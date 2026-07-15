package com.example.ec.dto;

import lombok.Getter;

/**
 * 管理画面ダッシュボードの売上棒グラフ表示用DTO。
 * 「日別」「週別」「月別」の売上推移グラフと「カテゴリ別」売上グラフの両方で、
 * 1本の棒＝(表示ラベル, 売上合計金額)という同じ形になるため共通で使う。
 * labelは日付文字列（例: "07/13"）、週の開始日（例: "07/07週"）、年月（例: "2026/07"）、
 * カテゴリ名（例: "キッチン用品"）のいずれかが入る。
 */
@Getter
public class SalesPoint {

    private final String label; // 棒の下に表示するラベル（日付・週・年月・カテゴリ名など）
    private final long total;   // このラベルに対応する売上合計金額（円）

    public SalesPoint(String label, long total) {
        this.label = label;
        this.total = total;
    }
}
