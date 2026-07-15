package com.example.ec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * パスワード再設定フォームの入力値を受け取るDTO。
 * メールで送付されたトークンと新しいパスワードを保持する。
 */
// @Getter はLombokのアノテーションで、全フィールドに対するgetterメソッド(getToken()等)を自動生成する
@Getter
// @Setter はLombokのアノテーションで、全フィールドに対するsetterメソッド(setToken()等)を自動生成する
@Setter
public class ResetPasswordForm {

    // メール内リンクに埋め込まれる再設定用トークン。正当性はサーバー側で検証する
    // @NotBlank により、null・空文字・空白のみの入力はエラーとする(メッセージ指定なしのためデフォルトメッセージが使われる)
    @NotBlank
    private String token;

    // 新しいパスワードの入力値。@NotBlank で未入力を禁止し、
    // @Size(min = 8) で8文字未満の場合はエラーとする
    @NotBlank(message = "新しいパスワードを入力してください")
    @Size(min = 8, message = "パスワードは8文字以上で入力してください")
    private String password;
}
