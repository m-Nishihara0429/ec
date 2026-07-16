package com.example.ec.dto;

import com.example.ec.entity.DiscountType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 管理画面のクーポン登録・編集フォームの入力値を受け取るDTO。
 * 新規登録・更新の両方で使用し、更新時はidに既存クーポンのIDが入る。
 */
@Getter
@Setter
public class CouponForm {

    // 新規登録時はnull、編集時は対象クーポンのIDが設定される
    private Long id;

    // クーポンコードの入力値。@NotBlank により、null・空文字・空白のみの入力はエラーとする。
    // @Size はエンティティ側の列長（Coupon.code, length=30）に合わせ、超過時は保存時の
    // 未処理DataIntegrityViolationException（生の500エラー）ではなくフォームの入力エラーとして扱う
    @NotBlank(message = "クーポンコードを入力してください")
    @Size(max = 30, message = "クーポンコードは30文字以内で入力してください")
    private String code;

    // 割引方式（率 or 固定額）。未選択はエラーとする
    @NotNull(message = "割引方式を選択してください")
    private DiscountType discountType;

    // 割引率(1-100)または割引額(円)。@Min(1)で0以下の入力はエラーとする
    @NotNull(message = "割引額（率）を入力してください")
    @Min(value = 1, message = "1以上で入力してください")
    private Integer discountValue;

    // このクーポンを適用できる最低注文金額。未入力の場合は0（制限なし）として扱う
    @Min(value = 0, message = "0以上で入力してください")
    private Integer minOrderAmount;

    // 総利用可能回数の上限。未入力（null）の場合は無制限として扱う
    @Min(value = 1, message = "1以上で入力してください")
    private Integer usageLimit;

    // 有効期間の開始日。未入力の場合は開始日の制限なし
    private LocalDate validFrom;

    // 有効期間の終了日。未入力の場合は終了日の制限なし
    private LocalDate validUntil;

    // 有効・無効フラグ。デフォルトは有効
    private boolean active = true;
}
