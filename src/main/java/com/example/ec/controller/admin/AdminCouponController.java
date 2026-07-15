package com.example.ec.controller.admin;

import com.example.ec.dto.CouponForm;
import com.example.ec.entity.Coupon;
import com.example.ec.entity.DiscountType;
import com.example.ec.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 管理者専用：クーポン（割引）の一覧表示・新規登録・編集・削除を担当するコントローラー。
 * クラスに {@code @RequestMapping("/admin/coupons")} が付与されているため、
 * 各メソッドのURLは「/admin/coupons」を起点とした相対パスになる。
 * 新規登録・編集どちらも同じ入力フォーム（CouponForm）と同じ登録テンプレートを共用する。
 */
@Controller
@RequestMapping("/admin/coupons")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /**
     * GET /admin/coupons
     * クーポン一覧画面を表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/coupons"
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("coupons", couponService.findAll());
        return "admin/coupons";
    }

    /**
     * GET /admin/coupons/new
     * クーポンの新規登録フォームを表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/coupon_form"
     */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("couponForm", new CouponForm());
        model.addAttribute("discountTypes", DiscountType.values());
        return "admin/coupon_form";
    }

    /**
     * GET /admin/coupons/{id}/edit
     * 既存クーポンの編集フォームを表示する。既存のクーポン情報をフォームDTOに詰め替えて渡す。
     *
     * @param id    編集対象のクーポンID（URLパス変数）
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/coupon_form"
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Coupon coupon = couponService.findById(id);
        CouponForm form = new CouponForm();
        form.setId(coupon.getId());
        form.setCode(coupon.getCode());
        form.setDiscountType(coupon.getDiscountType());
        form.setDiscountValue(coupon.getDiscountValue());
        form.setMinOrderAmount(coupon.getMinOrderAmount());
        form.setUsageLimit(coupon.getUsageLimit());
        form.setValidFrom(coupon.getValidFrom());
        form.setValidUntil(coupon.getValidUntil());
        form.setActive(coupon.isActive());
        model.addAttribute("couponForm", form);
        model.addAttribute("discountTypes", DiscountType.values());
        return "admin/coupon_form";
    }

    /**
     * POST /admin/coupons
     * クーポンを新規登録または更新する（フォームDTOにIDがあれば更新、無ければ新規登録）。
     * バリデーションエラー、およびクーポンコードの重複時は入力フォーム画面を再表示する。
     *
     * @param form          送信されたクーポン内容。{@code @Valid}で入力チェックを行う
     * @param bindingResult バリデーション結果
     * @param model         画面に渡すデータの入れ物
     * @return エラー時は "admin/coupon_form"、成功時はクーポン一覧画面へのリダイレクト
     */
    @PostMapping
    public String save(@Valid @ModelAttribute("couponForm") CouponForm form,
                        BindingResult bindingResult,
                        Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("discountTypes", DiscountType.values());
            return "admin/coupon_form";
        }
        try {
            couponService.save(form);
        } catch (IllegalArgumentException e) {
            // クーポンコードの重複などの業務エラーはエラーメッセージ付きで同じ画面に戻す
            model.addAttribute("discountTypes", DiscountType.values());
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/coupon_form";
        }
        return "redirect:/admin/coupons";
    }

    /**
     * POST /admin/coupons/{id}/delete
     * 指定したクーポンを削除する。既に削除済み（二重送信等）や、注文から参照中で
     * 削除できない場合はDataAccessExceptionが発生しうるため、生の500エラーを見せず
     * エラーメッセージ付きで一覧画面に戻す。
     *
     * @param id                 削除対象のクーポンID（URLパス変数）
     * @param redirectAttributes 削除失敗時のエラーメッセージをフラッシュ属性として伝えるための機構
     * @return クーポン一覧画面へリダイレクト（"redirect:/admin/coupons"）
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            couponService.deleteById(id);
        } catch (DataAccessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "このクーポンを削除できませんでした。既に削除済みか、注文で使用されている可能性があります。");
        }
        return "redirect:/admin/coupons";
    }
}
