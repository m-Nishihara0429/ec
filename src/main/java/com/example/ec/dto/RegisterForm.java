package com.example.ec.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 会員登録フォームの入力値を受け取るDTO。
 * バリデーションアノテーションでサーバー側の入力チェックを行う。
 */
// @Getter はLombokのアノテーションで、全フィールドに対する getName()等のgetterメソッドを自動生成する
@Getter
// @Setter はLombokのアノテーションで、全フィールドに対する setName()等のsetterメソッドを自動生成する
@Setter
public class RegisterForm {

    // 氏名の入力値。@NotBlank により、null・空文字・空白のみの入力はエラーとする。
    // @Size はエンティティ側の列長（User.name, length=100）に合わせ、超過時は未処理の
    // DataIntegrityViolationException（未ログインでも到達しうる生の500エラー）ではなく入力エラーとして扱う
    @NotBlank(message = "氏名を入力してください")
    @Size(max = 100, message = "氏名は100文字以内で入力してください")
    private String name;

    // メールアドレスの入力値。
    // @NotBlank で未入力を禁止し、@Email でメールアドレスの形式(xxx@yyy等)であることを検証する。
    // @Size はエンティティ側の列長（User.email, length=255）に合わせる
    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形式が正しくありません")
    @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
    private String email;

    // パスワードは8文字以上を必須とする業務ルール
    // パスワードの入力値。@NotBlank で未入力を禁止し、
    // @Size(min = 8) で8文字未満の場合はエラーとする
    @NotBlank(message = "パスワードを入力してください")
    @Size(min = 8, message = "パスワードは8文字以上で入力してください")
    private String password;
}
