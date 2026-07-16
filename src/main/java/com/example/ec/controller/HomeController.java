package com.example.ec.controller;

import com.example.ec.dto.ProductSort;
import com.example.ec.entity.Category;
import com.example.ec.entity.Product;
import com.example.ec.service.CategoryService;
import com.example.ec.service.ProductService;
import com.example.ec.service.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * トップページ（商品一覧の検索・絞り込み・並び替え・ページング）を担当するコントローラー。
 * ログイン不要で誰でもアクセスできる画面である。
 */
@Controller
public class HomeController {

    // 商品の検索・ページング・価格の最小値／最大値取得を行うサービス
    private final ProductService productService;
    // カテゴリ一覧の取得を行うサービス
    private final CategoryService categoryService;
    // 商品ごとの平均評価をまとめて取得するサービス
    private final ReviewService reviewService;

    // コンストラクタインジェクションで各サービスを受け取る
    public HomeController(ProductService productService, CategoryService categoryService, ReviewService reviewService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.reviewService = reviewService;
    }

    /**
     * GET /
     * トップページを表示する。カテゴリ・キーワード・価格帯での絞り込み、並び替え、
     * ページングに対応した商品一覧を表示する。
     *
     * @param categoryId 絞り込み対象のカテゴリID（未指定なら絞り込みなし）
     * @param keyword    絞り込み対象の検索キーワード（未指定なら絞り込みなし）
     * @param minPrice   絞り込み対象の最低価格（未指定なら下限なし）
     * @param maxPrice   絞り込み対象の最高価格（未指定なら上限なし）
     * @param sort       並び替え方法。未指定の場合は新着順（NEWEST）
     * @param page       表示するページ番号（0始まり）。未指定の場合は先頭ページ
     * @param model      画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "index"
     */
    @GetMapping("/")
    public String index(@RequestParam(required = false) Long categoryId,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) Integer minPrice,
                         @RequestParam(required = false) Integer maxPrice,
                         @RequestParam(defaultValue = "NEWEST") ProductSort sort,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        // 指定された検索条件・並び順・ページ情報（1ページ12件）で商品を検索する
        Page<Product> products = productService.search(categoryId, keyword, minPrice, maxPrice, sort,
                PageRequest.of(page, 12));
        // 検索結果に含まれる商品IDの一覧から、商品ごとの平均評価をまとめて取得する
        Map<Long, Double> ratings = reviewService.averageRatingsFor(
                products.getContent().stream().map(Product::getId).toList());

        // 検索結果の商品ページ情報（商品一覧＋ページング情報）を画面に渡す
        model.addAttribute("products", products);
        // 商品IDごとの平均評価を画面に渡す（商品カードの星表示などに使う）
        model.addAttribute("ratings", ratings);
        // 絞り込み用のカテゴリ一覧（全件）を画面に渡す
        model.addAttribute("categories", categoryService.findAll());
        // 現在選択中のカテゴリIDを画面に渡す（プルダウンの選択状態維持などに使う）
        model.addAttribute("selectedCategoryId", categoryId);
        // 現在の検索キーワードを画面に渡す（検索ボックスの入力値維持に使う）
        model.addAttribute("keyword", keyword);
        // 現在の最低価格条件を画面に渡す
        model.addAttribute("minPrice", minPrice);
        // 現在の最高価格条件を画面に渡す
        model.addAttribute("maxPrice", maxPrice);
        // 価格帯スライダー等の下限値として、全商品中の最安値を画面に渡す
        model.addAttribute("priceFloor", productService.findMinPrice());
        // 価格帯スライダー等の上限値として、全商品中の最高値を画面に渡す
        model.addAttribute("priceCeil", productService.findMaxPrice());
        // 現在選択中の並び替え条件を画面に渡す
        model.addAttribute("sort", sort);
        // 並び替えプルダウンの選択肢一覧（enumの全要素）を画面に渡す
        model.addAttribute("sortValues", ProductSort.values());
        // 右サイドレール「おすすめ商品」表示用。絞り込み条件とは無関係に、新着順で4件だけ取得する
        Page<Product> recommended = productService.search(null, null, null, null,
                ProductSort.NEWEST, PageRequest.of(0, 4));
        model.addAttribute("recommendedProducts", recommended.getContent());

        // ページ下部の「カテゴリ別のおすすめ」棚表示用。カテゴリごとに新着順で最大4件を取得する。
        // 商品が1件もないカテゴリは棚自体を表示しないため、あらかじめ除外しておく
        // （LinkedHashMapを使い、カテゴリの登録順を表示順として保つ）
        Map<Category, List<Product>> shelvesByCategory = new LinkedHashMap<>();
        for (Category category : categoryService.findAll()) {
            Page<Product> shelfProducts = productService.search(category.getId(), null, null, null,
                    ProductSort.NEWEST, PageRequest.of(0, 4));
            if (!shelfProducts.isEmpty()) {
                shelvesByCategory.put(category, shelfProducts.getContent());
            }
        }
        model.addAttribute("shelvesByCategory", shelvesByCategory);

        // ページ最下部の横スクロール一覧表示用。閲覧履歴等の追跡は行っていないため、
        // 「あなたへのおすすめ」のような個人化された見出しは使わず、新着順の全商品を横並びで見せる
        Page<Product> browseStrip = productService.search(null, null, null, null,
                ProductSort.NEWEST, PageRequest.of(0, 20));
        model.addAttribute("browseStripProducts", browseStrip.getContent());
        // index.html（Thymeleafテンプレート）を表示する
        return "index";
    }
}
