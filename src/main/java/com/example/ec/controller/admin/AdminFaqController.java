package com.example.ec.controller.admin;

import com.example.ec.dto.FaqForm;
import com.example.ec.entity.Faq;
import com.example.ec.service.FaqService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * 管理者専用：FAQ(よくある質問)の一覧表示・新規登録・編集・削除を担当するコントローラー。
 * クラスに {@code @RequestMapping("/admin/faqs")} が付与されているため、
 * 各メソッドのURLは「/admin/faqs」を起点とした相対パスになる。
 */
@Controller
@RequestMapping("/admin/faqs")
public class AdminFaqController {

    private final FaqService faqService;

    public AdminFaqController(FaqService faqService) {
        this.faqService = faqService;
    }

    /**
     * GET /admin/faqs
     * FAQ一覧画面を表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/faqs"
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("faqs", faqService.findAll());
        return "admin/faqs";
    }

    /**
     * GET /admin/faqs/new
     * FAQの新規登録フォームを表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/faq_form"
     */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("faqForm", new FaqForm());
        return "admin/faq_form";
    }

    /**
     * GET /admin/faqs/{id}/edit
     * 既存FAQの編集フォームを表示する。既存のFAQ情報をフォームDTOに詰め替えて渡す。
     *
     * @param id    編集対象のFAQ ID（URLパス変数）
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/faq_form"
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Faq faq = faqService.findById(id);
        FaqForm form = new FaqForm();
        form.setId(faq.getId());
        form.setQuestion(faq.getQuestion());
        form.setAnswer(faq.getAnswer());
        form.setDisplayOrder(faq.getDisplayOrder());
        model.addAttribute("faqForm", form);
        return "admin/faq_form";
    }

    /**
     * POST /admin/faqs
     * FAQを新規登録または更新する（フォームDTOにIDがあれば更新、無ければ新規登録）。
     * バリデーションエラー時は入力フォーム画面を再表示する。
     *
     * @param form          送信されたFAQ内容。{@code @Valid}で入力チェックを行う
     * @param bindingResult バリデーション結果
     * @return エラー時は "admin/faq_form"、成功時はFAQ一覧画面へのリダイレクト
     */
    @PostMapping
    public String save(@Valid @ModelAttribute("faqForm") FaqForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "admin/faq_form";
        }
        faqService.save(form);
        return "redirect:/admin/faqs";
    }

    /**
     * POST /admin/faqs/{id}/delete
     * 指定したFAQを削除する。
     *
     * @param id 削除対象のFAQ ID（URLパス変数）
     * @return FAQ一覧画面へリダイレクト（"redirect:/admin/faqs"）
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        faqService.deleteById(id);
        return "redirect:/admin/faqs";
    }
}
