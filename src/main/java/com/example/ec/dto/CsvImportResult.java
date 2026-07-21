package com.example.ec.dto;

import lombok.Getter;

import java.util.List;

/**
 * 商品CSV一括登録処理の結果を表すDTO。
 * 1行の不備で全体を失敗させず、行単位でスキップして処理を続けるため、
 * 登録できた件数とスキップした行の理由一覧の両方を画面に伝える必要がある。
 */
@Getter
public class CsvImportResult {

    // 正常に登録できた商品の件数
    private final int successCount;
    // スキップした行の理由一覧（例: "3行目: 価格・在庫数は数値で入力してください"）
    private final List<String> errors;

    public CsvImportResult(int successCount, List<String> errors) {
        this.successCount = successCount;
        this.errors = errors;
    }
}
