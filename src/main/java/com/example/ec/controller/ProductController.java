package com.example.ec.controller;

import com.example.ec.config.SecurityUserDetails;
import com.example.ec.dto.ReviewForm;
import com.example.ec.entity.Product;
import com.example.ec.service.ProductService;
import com.example.ec.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * 商品詳細ページの表示とレビュー投稿を担当するコントローラー。
 * 商品詳細画面ではレビュー一覧・平均評価・レビュー件数もあわせて表示し、
 * ログイン中ユーザーはその画面からレビューを投稿できる。
 */
@Controller
public class ProductController {

    // 商品情報の取得を行うサービス
    private final ProductService productService;
    // レビューの取得・投稿・集計を行うサービス
    private final ReviewService reviewService;

    // コンストラクタインジェクションで各サービスを受け取る
    public ProductController(ProductService productService, ReviewService reviewService) {
        this.productService = productService;
        this.reviewService = reviewService;
    }

    /**
     * GET /products/{id}
     * 商品詳細画面を表示する。あわせてその商品のレビュー一覧・平均評価・件数、
     * レビュー投稿フォームの初期値も画面に渡す。
     *
     * @param id    表示する商品のID（URLパス変数）
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "product/detail"
     */
    @GetMapping("/products/{id}")
    public String detail(@PathVariable Long id, Model model) {
        // パス変数で指定された商品IDから商品エンティティを取得する
        Product product = productService.findById(id);
        // 商品情報を画面に渡す
        model.addAttribute("product", product);
        // その商品に紐づくレビュー一覧を画面に渡す
        model.addAttribute("reviews", reviewService.findByProduct(product));
        // その商品の平均評価（星の数など）を画面に渡す
        model.addAttribute("averageRating", reviewService.averageRating(product));
        // その商品のレビュー件数を画面に渡す
        model.addAttribute("reviewCount", reviewService.reviewCount(product));
        // バリデーションエラーでリダイレクトされてきた場合はModelに既にreviewFormが
        // 詰められているため、まだ存在しない場合のみ新規の空フォームを用意する
        if (!model.containsAttribute("reviewForm")) {
            model.addAttribute("reviewForm", new ReviewForm());
        }
        // product/detail.html（Thymeleafテンプレート）を表示する
        return "product/detail";
    }

    /**
     * POST /products/{id}/reviews
     * 指定した商品にログイン中ユーザーのレビューを投稿する。
     * バリデーションエラーがある場合は商品詳細画面を再表示する。
     *
     * @param id             レビュー対象の商品ID（URLパス変数）
     * @param principal      ログイン中ユーザーの情報
     * @param form            投稿されたレビュー内容（評価・コメントなど）。{@code @Valid}で入力チェックを行う
     * @param bindingResult   バリデーション結果（エラーの有無・内容を保持する）
     * @param model           画面に渡すデータの入れ物
     * @return バリデーションエラー時は "product/detail"、成功時は商品詳細画面へのリダイレクト
     */
    @PostMapping("/products/{id}/reviews")
    public String submitReview(@PathVariable Long id,
                                @AuthenticationPrincipal SecurityUserDetails principal,
                                @Valid @ModelAttribute("reviewForm") ReviewForm form,
                                BindingResult bindingResult,
                                Model model) {
        // レビュー投稿先の商品エンティティを取得する
        Product product = productService.findById(id);
        // @Validによるバリデーションでエラーがあったかどうかを判定する
        if (bindingResult.hasErrors()) {
            // バリデーションエラー時は商品詳細に必要な情報を再度詰め直し、同じ画面を再表示する
            model.addAttribute("product", product);
            model.addAttribute("reviews", reviewService.findByProduct(product));
            model.addAttribute("averageRating", reviewService.averageRating(product));
            model.addAttribute("reviewCount", reviewService.reviewCount(product));
            // エラー内容（bindingResultの中身）はModel経由で自動的に画面へ引き継がれる
            return "product/detail";
        }
        // バリデーションOKならレビューを登録する（投稿者はログイン中ユーザー）
        reviewService.submitReview(product, principal.getUser(), form);
        // 投稿後は同じ商品の詳細画面へリダイレクトし、最新のレビュー一覧を表示させる
        return "redirect:/products/" + id;
    }
}
