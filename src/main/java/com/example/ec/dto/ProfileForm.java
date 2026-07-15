package com.example.ec.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 会員プロフィール編集フォームの入力値を受け取るDTO。
 * 氏名の更新に使用する。
 */
// @Getter はLombokのアノテーションで、全フィールドに対するgetterメソッド(getName())を自動生成する
@Getter
// @Setter はLombokのアノテーションで、全フィールドに対するsetterメソッド(setName())を自動生成する
@Setter
public class ProfileForm {

    // 氏名の入力値。@NotBlank により、null・空文字・空白のみの入力はエラーとする
    @NotBlank(message = "氏名を入力してください")
    private String name;
}
