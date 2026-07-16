package com.example.ec.controller.admin;

import com.example.ec.entity.Category;
import com.example.ec.service.CategoryService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
     * 名前が空・文字数超過・重複の場合はサービス層で例外が発生し、
     * フラッシュメッセージとしてエラー内容を一覧画面に伝える。
     *
     * @param name               登録するカテゴリ名（フォームのリクエストパラメータ）
     * @param redirectAttributes 登録失敗時のエラーメッセージをフラッシュ属性として伝えるための機構
     * @return カテゴリ一覧画面へリダイレクト（"redirect:/admin/categories"）
     */
    @PostMapping
    public String save(@RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            // 指定された名前で新しいカテゴリエンティティを作成して保存する
            categoryService.save(new Category(name));
        } catch (IllegalArgumentException e) {
            // 名前が空・文字数超過・重複だった場合、エラーメッセージをフラッシュ属性に設定する
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        // 登録後はカテゴリ一覧画面へリダイレクトする（PRGパターンで二重送信を防ぐ）
        return "redirect:/admin/categories";
    }

    /**
     * POST /admin/categories/{id}/delete
     * 指定したカテゴリを削除する。
     * 既に削除済み（二重送信等）や、商品から参照中で削除できない場合はDataAccessExceptionが
     * 発生しうるため、生の500エラーを見せずエラーメッセージ付きで一覧画面に戻す。
     *
     * @param id                 削除するカテゴリのID（URLパス変数）
     * @param redirectAttributes 削除失敗時のエラーメッセージをフラッシュ属性として伝えるための機構
     * @return カテゴリ一覧画面へリダイレクト（"redirect:/admin/categories"）
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // 指定されたIDのカテゴリを削除する
            categoryService.deleteById(id);
        } catch (DataAccessException e) {
            // 既に削除済み、または商品から参照中で削除できない等の理由をエラーメッセージとして表示する
            redirectAttributes.addFlashAttribute("errorMessage", "このカテゴリを削除できませんでした。既に削除済みか、商品から参照されている可能性があります。");
        }
        // 削除後はカテゴリ一覧画面へリダイレクトする
        return "redirect:/admin/categories";
    }
}
