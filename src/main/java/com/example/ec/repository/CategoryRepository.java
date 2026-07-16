package com.example.ec.repository;

import com.example.ec.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 商品カテゴリを管理するリポジトリ。
 * JpaRepositoryが提供する標準のCRUD操作のみを利用する。
 * JpaRepository<Category, Long> を継承することで、
 * save(保存/更新)・findById(主キー検索)・findAll(全件取得)・delete(削除)などの
 * 基本的なCRUD操作が自動的に使えるようになる。
 * Category はエンティティ型、Long は主キー(id)の型を表す。
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {
    // 新規カテゴリ登録時の重複チェック用。同名カテゴリが既に存在するかを確認する
    boolean existsByName(String name);
}
