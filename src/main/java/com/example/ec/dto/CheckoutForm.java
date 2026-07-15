package com.example.ec.dto;

import com.example.ec.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 注文確定（チェックアウト）画面の入力値を受け取るDTO。
 * 配送先住所・支払い方法の入力チェック、およびクーポンコード（任意）の受け取りに使用する。
 */
// @Getter はLombokのアノテーションで、全フィールドに対する getAddress()等のgetterメソッドを自動生成する
@Getter
// @Setter はLombokのアノテーションで、全フィールドに対する setAddress()等のsetterメソッドを自動生成する
@Setter
public class CheckoutForm {

    // 配送先住所の入力値。@NotBlank により、null・空文字・空白のみの入力はエラーとする
    @NotBlank(message = "配送先住所を入力してください")
    private String address;

    // 支払い方法。@NotNull により未選択はエラーとする
    @NotNull(message = "支払い方法を選択してください")
    private PaymentMethod paymentMethod;

    // クーポンコードの入力値。任意項目のためバリデーションアノテーションは付けない
    private String couponCode;
}
