package com.example.ec.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 問い合わせフォームの入力値を受け取るDTO。
 */
@Getter
@Setter
public class ContactForm {

    @NotBlank(message = "お名前を入力してください")
    private String name;

    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;

    @NotBlank(message = "件名を入力してください")
    private String subject;

    @NotBlank(message = "お問い合わせ内容を入力してください")
    private String message;
}
