package com.example.ec.config;

import com.example.ec.entity.Category;
import com.example.ec.service.CategoryService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * 全コントローラーに共通のモデル属性を差し込むクラス。
 * ここに定義した属性は、個々のコントローラーで明示的に追加しなくても全画面のModelに自動で入る。
 * {@code @ControllerAdvice} が付与されたクラスは、Spring MVCがすべてのコントローラー呼び出しに対して
 * 横断的に適用する（AOP的な仕組み）。
 */
@ControllerAdvice
public class GlobalModelAdvice {

    // カテゴリ一覧を取得するためのサービス
    private final CategoryService categoryService;

    /**
     * コンストラクタ。Spring DIによってCategoryServiceが自動的に注入される。
     * @param categoryService カテゴリ取得用サービス
     */
    public GlobalModelAdvice(CategoryService categoryService) {
        // フィールドに保持する
        this.categoryService = categoryService;
    }

    /**
     * カテゴリ一覧を"navCategories"としてすべてのModelに追加する。
     * トップページ以外の画面（fragments/layout.htmlのハンバーガーメニュー／ドロワー）でも
     * カテゴリリンクを表示できるようにするための共通処理。
     * @return 全カテゴリのリスト（表示用ナビゲーションに使う）
     */
    @ModelAttribute("navCategories")
    public List<Category> navCategories() {
        // カテゴリサービスから全件取得してそのまま返す（戻り値は自動的にModelへ格納される）
        return categoryService.findAll();
    }
}
