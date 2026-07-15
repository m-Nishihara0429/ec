package com.example.ec.controller;

import com.example.ec.dto.ContactForm;
import com.example.ec.service.InquiryService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 問い合わせフォームの表示・送信を担当するコントローラー。ログイン不要で誰でも利用できる。
 * メール送信基盤は持たないため、送信内容はDBに保存し管理画面で確認する運用を想定している。
 */
@Controller
public class ContactController {

    private final InquiryService inquiryService;

    public ContactController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    /**
     * GET /contact
     * 問い合わせフォームを表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "contact"
     */
    @GetMapping("/contact")
    public String form(Model model) {
        model.addAttribute("contactForm", new ContactForm());
        return "contact";
    }

    /**
     * POST /contact
     * 問い合わせ内容を保存する。バリデーションエラー時は同じ画面を再表示する。
     *
     * @param form          送信された問い合わせ内容。{@code @Valid}で入力チェックを行う
     * @param bindingResult バリデーション結果
     * @return エラー時は "contact"、成功時は送信完了パラメータ付きで同画面へリダイレクト
     */
    @PostMapping("/contact")
    public String submit(@Valid @ModelAttribute("contactForm") ContactForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "contact";
        }
        inquiryService.save(form);
        // 送信完了をクエリパラメータで示しつつ同じ問い合わせフォーム画面へリダイレクトする（PRGパターンで二重送信を防ぐ）
        return "redirect:/contact?sent";
    }
}
