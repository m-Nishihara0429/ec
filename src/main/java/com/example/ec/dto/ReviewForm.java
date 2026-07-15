package com.example.ec.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 商品レビュー投稿フォームの入力値を受け取るDTO。
 * 星評価とコメント本文を保持する。
 */
// @Getter はLombokのアノテーションで、全フィールドに対するgetterメソッド(getRating()等)を自動生成する
@Getter
// @Setter はLombokのアノテーションで、全フィールドに対するsetterメソッド(setRating()等)を自動生成する
@Setter
public class ReviewForm {

    // 星評価は1〜5段階の範囲内のみ許可する
    // 評価(星の数)の入力値。@NotNull で未入力(null)を禁止し、
    // @Min(value = 1) / @Max(value = 5) で1〜5の範囲外の値はエラーとする
    @NotNull(message = "評価を選択してください")
    @Min(value = 1, message = "評価は1〜5で選択してください")
    @Max(value = 5, message = "評価は1〜5で選択してください")
    private Integer rating;

    // レビューのコメント本文。バリデーションアノテーションはなく、任意入力(空でも可)
    private String comment;
}
