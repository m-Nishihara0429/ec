package com.example.ec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理画面の商品登録・編集フォームの入力値を受け取るDTO。
 * 新規登録・更新の両方で使用し、更新時はidに既存商品のIDが入る。
 */
// @Getter はLombokのアノテーションで、全フィールドに対するgetterメソッド(getName()等)を自動生成する
@Getter
// @Setter はLombokのアノテーションで、全フィールドに対するsetterメソッド(setName()等)を自動生成する
@Setter
public class ProductForm {

    // 新規登録時はnull、編集時は対象商品のIDが設定される
    // バリデーションアノテーションは付いていない(新規登録時にnullを許容する必要があるため)
    private Long id;

    // 商品名の入力値。@NotBlank により、null・空文字・空白のみの入力はエラーとする。
    // @Size はエンティティ側の列長（Product.name, length=200）に合わせる
    @NotBlank(message = "商品名を入力してください")
    @Size(max = 200, message = "商品名は200文字以内で入力してください")
    private String name;

    // 商品説明の入力値。バリデーションアノテーションはなく、任意入力(空でも可)
    private String description;

    // 価格の入力値。@NotNull で未入力(null)を禁止し、@Min(value = 0) で
    // 0未満(マイナスの価格)の入力はエラーとする
    @NotNull(message = "価格を入力してください")
    @Min(value = 0, message = "価格は0以上で入力してください")
    private Integer price;

    // 在庫数の入力値。@NotNull で未入力(null)を禁止し、@Min(value = 0) で
    // 0未満(マイナスの在庫)の入力はエラーとする
    @NotNull(message = "在庫数を入力してください")
    @Min(value = 0, message = "在庫数は0以上で入力してください")
    private Integer stock;

    // 商品画像のURL文字列。任意入力(空でも可)だが、@Size はエンティティ側の列長
    // （Product.imageUrl, length=500）に合わせる
    @Size(max = 500, message = "画像URLは500文字以内で入力してください")
    private String imageUrl;

    // 紐づけるカテゴリのID。バリデーションアノテーションはなく、任意入力(未選択でも可)
    private Long categoryId;
}
