package com.example.ec.controller.admin;

import com.example.ec.entity.Category;
import com.example.ec.service.CategoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 管理者専用：カテゴリの一覧表示・登録・削除を担当するコントローラー。
 * クラスに {@code @RequestMapping("/admin/categories")} が付与されているため、
 * 各メソッドのURLは「/admin/categories」を起点とした相対パスになる。
 * このパス配下へのアクセス制御（管理者権限が必要かどうか）はSpring Securityの設定側で行われる想定。
 */
@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    // カテゴリの一覧取得・保存・削除を行うサービス
    private final CategoryService categoryService;

    // コンストラクタインジェクションでサービスを受け取る
    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * GET /admin/categories
     * カテゴリ一覧画面を表示する。新規登録フォーム用の空DTOもあわせて渡す。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/categories"
     */
    @GetMapping
    public String list(Model model) {
        // カテゴリの全件一覧を取得して画面に渡す
        model.addAttribute("categories", categoryService.findAll());
        // 新規カテゴリ登録フォーム用の空エンティティを画面に渡す
        model.addAttribute("newCategory", new Category());
        // admin/categories.html（Thymeleafテンプレート）を表示する
        return "admin/categories";
    }

    /**
     * POST /admin/categories
     * 新しいカテゴリを登録する。
     *
     * @param name 登録するカテゴリ名（フォームのリクエストパラメータ）
     * @return カテゴリ一覧画面へリダイレクト（"redirect:/admin/categories"）
     */
    @PostMapping
    public String save(@RequestParam String name) {
        // 指定された名前で新しいカテゴリエンティティを作成して保存する
        categoryService.save(new Category(name));
        // 登録後はカテゴリ一覧画面へリダイレクトする（PRGパターンで二重送信を防ぐ）
        return "redirect:/admin/categories";
    }

    /**
     * POST /admin/categories/{id}/delete
     * 指定したカテゴリを削除する。
     *
     * @param id 削除するカテゴリのID（URLパス変数）
     * @return カテゴリ一覧画面へリダイレクト（"redirect:/admin/categories"）
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        // 指定されたIDのカテゴリを削除する
        categoryService.deleteById(id);
        // 削除後はカテゴリ一覧画面へリダイレクトする
        return "redirect:/admin/categories";
    }
}
