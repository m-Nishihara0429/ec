package com.example.ec.controller.admin;

import com.example.ec.dto.ProductForm;
import com.example.ec.dto.ProductSort;
import com.example.ec.entity.Product;
import com.example.ec.service.CategoryService;
import com.example.ec.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 管理者専用：商品の一覧表示・新規登録・編集・削除を担当するコントローラー。
 * クラスに {@code @RequestMapping("/admin/products")} が付与されているため、
 * 各メソッドのURLは「/admin/products」を起点とした相対パスになる。
 * 新規登録・編集どちらも同じ入力フォーム（ProductForm）と同じ登録テンプレートを共用する。
 */
@Controller
@RequestMapping("/admin/products")
public class AdminProductController {

    // 商品の検索・保存・削除を行うサービス
    private final ProductService productService;
    // カテゴリ一覧取得（フォームのプルダウン用）を行うサービス
    private final CategoryService categoryService;

    // コンストラクタインジェクションで各サービスを受け取る
    public AdminProductController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    /**
     * GET /admin/products
     * 商品一覧画面をページング付きで表示する。
     *
     * @param page  表示するページ番号（0始まり）。未指定の場合は先頭ページ
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/products"
     */
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        // 絞り込み条件なし・新着順・1ページ20件で商品を検索する（管理者用の全件一覧のため検索条件は使わない）
        Page<Product> products = productService.search(null, null, null, null, ProductSort.NEWEST, PageRequest.of(page, 20));
        // 検索結果（商品一覧＋ページング情報）を画面に渡す
        model.addAttribute("products", products);
        // admin/products.html（Thymeleafテンプレート）を表示する
        return "admin/products";
    }

    /**
     * GET /admin/products/new
     * 商品の新規登録フォームを表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/product_form"
     */
    @GetMapping("/new")
    public String newForm(Model model) {
        // 新規登録用の空フォームDTOを画面に渡す
        model.addAttribute("productForm", new ProductForm());
        // カテゴリ選択プルダウン用にカテゴリ一覧を画面に渡す
        model.addAttribute("categories", categoryService.findAll());
        // admin/product_form.html（Thymeleafテンプレート）を表示する（新規登録・編集共通のフォーム画面）
        return "admin/product_form";
    }

    /**
     * GET /admin/products/{id}/edit
     * 既存商品の編集フォームを表示する。既存の商品情報をフォームDTOに詰め替えて渡す。
     *
     * @param id    編集対象の商品ID（URLパス変数）
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/product_form"
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        // パス変数で指定された商品IDから既存の商品エンティティを取得する
        Product product = productService.findById(id);
        // 既存商品のエンティティを編集フォーム用のDTOに詰め替える
        ProductForm form = new ProductForm();
        // 更新対象を特定するためIDをセットする（保存時にこのIDの有無で新規/更新を判定する想定）
        form.setId(product.getId());
        // 商品名をフォームにセットする
        form.setName(product.getName());
        // 商品説明をフォームにセットする
        form.setDescription(product.getDescription());
        // 価格をフォームにセットする
        form.setPrice(product.getPrice());
        // 在庫数をフォームにセットする
        form.setStock(product.getStock());
        // 商品画像URLをフォームにセットする
        form.setImageUrl(product.getImageUrl());
        // カテゴリが設定されている場合のみ、そのカテゴリIDをフォームにセットする（未分類の商品も存在しうるため）
        if (product.getCategory() != null) {
            form.setCategoryId(product.getCategory().getId());
        }
        // 詰め替えたフォームを画面に渡す
        model.addAttribute("productForm", form);
        // カテゴリ選択プルダウン用にカテゴリ一覧を画面に渡す
        model.addAttribute("categories", categoryService.findAll());
        // admin/product_form.html（Thymeleafテンプレート）を表示する
        return "admin/product_form";
    }

    /**
     * POST /admin/products
     * 商品を新規登録または更新する（フォームDTOにIDがあれば更新、無ければ新規登録とみなすのはサービス層の実装次第）。
     * バリデーションエラー時は入力フォーム画面を再表示する。
     *
     * @param form          送信された商品情報。{@code @Valid}で入力チェックを行う
     * @param bindingResult バリデーション結果
     * @param model         画面に渡すデータの入れ物
     * @return エラー時は "admin/product_form"、成功時は商品一覧画面へのリダイレクト
     */
    @PostMapping
    public String save(@Valid @ModelAttribute("productForm") ProductForm form,
                        BindingResult bindingResult,
                        Model model) {
        // 入力項目自体のバリデーションエラーがあれば、カテゴリ一覧を詰め直してフォーム画面を再表示する
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            return "admin/product_form";
        }
        try {
            // サービス層で商品情報を保存する（新規登録／更新の判定も含めてサービス層が担当）
            productService.save(form);
        } catch (IllegalArgumentException e) {
            // カテゴリが編集中に削除された等でcategoryIdが無効な場合、エラーメッセージ付きでフォームを再表示する
            model.addAttribute("categories", categoryService.findAll());
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/product_form";
        }
        // 保存後は商品一覧画面へリダイレクトする
        return "redirect:/admin/products";
    }

    /**
     * POST /admin/products/{id}/delete
     * 指定した商品を削除する。既に削除済み（二重送信等）や、注文・カートから参照中で
     * 削除できない場合はDataAccessExceptionが発生しうるため、生の500エラーを見せず
     * エラーメッセージ付きで一覧画面に戻す。
     *
     * @param id                 削除対象の商品ID（URLパス変数）
     * @param redirectAttributes 削除失敗時のエラーメッセージをフラッシュ属性として伝えるための機構
     * @return 商品一覧画面へリダイレクト（"redirect:/admin/products"）
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        AdminControllerSupport.safeDelete(() -> productService.deleteById(id), redirectAttributes,
                "この商品を削除できませんでした。既に削除済みか、注文やカートで参照されている可能性があります。");
        // 削除後は商品一覧画面へリダイレクトする
        return "redirect:/admin/products";
    }
}
