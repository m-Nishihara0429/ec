package com.example.ec.controller;

import com.example.ec.service.FaqService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * よくある質問(FAQ)ページを担当するコントローラー。ログイン不要で誰でも閲覧できる。
 */
@Controller
public class FaqController {

    private final FaqService faqService;

    public FaqController(FaqService faqService) {
        this.faqService = faqService;
    }

    /**
     * GET /faq
     * FAQ一覧画面を表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "faq"
     */
    @GetMapping("/faq")
    public String list(Model model) {
        model.addAttribute("faqs", faqService.findAll());
        return "faq";
    }
}
