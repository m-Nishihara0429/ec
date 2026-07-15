package com.example.ec.controller.admin;

import com.example.ec.service.InquiryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 管理者専用：問い合わせの一覧表示・詳細確認・削除を担当するコントローラー。
 * クラスに {@code @RequestMapping("/admin/inquiries")} が付与されているため、
 * 各メソッドのURLは「/admin/inquiries」を起点とした相対パスになる。
 */
@Controller
@RequestMapping("/admin/inquiries")
public class AdminInquiryController {

    private final InquiryService inquiryService;

    public AdminInquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    /**
     * GET /admin/inquiries
     * 問い合わせ一覧画面を表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/inquiries"
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("inquiries", inquiryService.findAll());
        return "admin/inquiries";
    }

    /**
     * GET /admin/inquiries/{id}
     * 問い合わせ詳細画面を表示する。
     *
     * @param id    表示する問い合わせのID（URLパス変数）
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/inquiry_detail"
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("inquiry", inquiryService.findById(id));
        return "admin/inquiry_detail";
    }

    /**
     * POST /admin/inquiries/{id}/delete
     * 指定した問い合わせを削除する。
     *
     * @param id 削除対象の問い合わせID（URLパス変数）
     * @return 問い合わせ一覧画面へリダイレクト（"redirect:/admin/inquiries"）
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        inquiryService.deleteById(id);
        return "redirect:/admin/inquiries";
    }
}
