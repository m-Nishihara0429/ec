package com.example.ec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * ログイン中の会員がパスワードを変更する際の入力値を受け取るDTO。
 * 本人確認のため現在のパスワードと新しいパスワードの両方を保持する。
 */
// @Getter はLombokのアノテーションで、全フィールドに対するgetterメソッド(getCurrentPassword()等)を自動生成する
@Getter
// @Setter はLombokのアノテーションで、全フィールドに対するsetterメソッド(setCurrentPassword()等)を自動生成する
@Setter
public class ChangePasswordForm {

    // なりすまし変更を防ぐため、現在のパスワードでの本人確認を必須とする
    // 現在のパスワードの入力値。@NotBlank により、null・空文字・空白のみの入力はエラーとする
    @NotBlank(message = "現在のパスワードを入力してください")
    private String currentPassword;

    // 新しいパスワードの入力値。@NotBlank で未入力を禁止し、
    // @Size(min = 8) で8文字未満の場合はエラーとする
    @NotBlank(message = "新しいパスワードを入力してください")
    @Size(min = 8, message = "パスワードは8文字以上で入力してください")
    private String newPassword;
}
