package com.example.ec.service;

import com.example.ec.entity.Category;
import com.example.ec.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品カテゴリの参照・登録・削除を行うサービスクラス。
 * コントローラー層とリポジトリ層の間に立ち、カテゴリに関するビジネスロジック
 * （このクラスでは主に「見つからない場合の例外変換」）をまとめている。
 * このクラス自体は状態を持たず、すべての処理をCategoryRepositoryに委譲する薄い層になっている。
 */
@Service // Springにこのクラスをサービス層のBeanとして登録させるアノテーション（DIコンテナに乗る）
public class CategoryService {

    // カテゴリのデータアクセスを担当するリポジトリ（コンストラクタで注入される）
    private final CategoryRepository categoryRepository;

    // コンストラクタインジェクション。Spring Bootが起動時にCategoryRepositoryの実装を自動的に渡してくれる
    public CategoryService(CategoryRepository categoryRepository) {
        // フィールドに保存し、以降のメソッドから利用できるようにする
        this.categoryRepository = categoryRepository;
    }

    /**
     * 登録されているカテゴリを全件取得する。
     *
     * @return カテゴリの一覧（0件の場合は空リスト）
     */
    public List<Category> findAll() {
        // リポジトリのfindAllをそのまま呼び出し、DB上の全カテゴリを返す
        return categoryRepository.findAll();
    }

    /**
     * IDを指定してカテゴリを1件取得する。
     *
     * @param id 取得したいカテゴリのID
     * @return 該当するカテゴリ
     * @throws IllegalArgumentException 指定したIDのカテゴリが存在しない場合
     */
    public Category findById(Long id) {
        // findByIdはOptionalを返すため、値が存在すればそのカテゴリを、存在しなければ例外を投げる
        return categoryRepository.findById(id)
                // 見つからない場合はエラーメッセージにIDを含めて例外化する
                .orElseThrow(() -> new IllegalArgumentException("カテゴリが見つかりません: " + id));
    }

    /**
     * カテゴリを保存する。IDが未設定なら新規登録、設定済みなら更新として動作する（JPAのsave仕様に準拠）。
     * カテゴリ名は前後の空白を除去したうえで、必須入力・文字数上限・重複を検証する。
     * 売上集計（管理画面ダッシュボード）がカテゴリ名でグルーピングしているため、
     * 重複する名前を許可すると別カテゴリの売上が合算されてしまう。
     *
     * @param category 保存対象のカテゴリ
     * @return 保存後（IDが採番された状態）のカテゴリ
     * @throws IllegalArgumentException カテゴリ名が空、100文字を超える、または既存の他カテゴリと重複する場合
     */
    @Transactional // 重複チェックと保存を1つの整合した処理としてまとめる
    public Category save(Category category) {
        // 入力ゆれを防ぐため、名前は前後の空白を除去する
        String normalizedName = category.getName() == null ? "" : category.getName().trim();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("カテゴリ名を入力してください");
        }
        if (normalizedName.length() > 100) {
            throw new IllegalArgumentException("カテゴリ名は100文字以内で入力してください");
        }
        if (categoryRepository.existsByName(normalizedName)) {
            throw new IllegalArgumentException("同じ名前のカテゴリが既に存在します");
        }
        category.setName(normalizedName);
        // JPAのsaveはIDの有無で新規/更新を自動判定してくれるため、そのまま委譲する
        return categoryRepository.save(category);
    }

    /**
     * 指定したIDのカテゴリを削除する。
     *
     * @param id 削除対象のカテゴリID
     */
    public void deleteById(Long id) {
        // リポジトリのdeleteByIdをそのまま呼び出して削除する
        categoryRepository.deleteById(id);
    }
}
