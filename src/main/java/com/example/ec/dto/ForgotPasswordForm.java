package com.example.ec.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * パスワード再設定申請（パスワードを忘れた場合）フォームの入力値を受け取るDTO。
 * 再設定用メール送信先のメールアドレスを保持する。
 */
// @Getter はLombokのアノテーションで、全フィールドに対するgetterメソッド(getEmail())を自動生成する
@Getter
// @Setter はLombokのアノテーションで、全フィールドに対するsetterメソッド(setEmail())を自動生成する
@Setter
public class ForgotPasswordForm {

    // 再設定メールの送信先メールアドレス。
    // @NotBlank で未入力を禁止し、@Email でメールアドレスの形式であることを検証する
    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;
}
