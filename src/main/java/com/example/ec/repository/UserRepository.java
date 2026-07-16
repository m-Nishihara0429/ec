package com.example.ec.repository;

import com.example.ec.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * ユーザー(会員)情報を管理するリポジトリ。
 * ログイン認証や会員登録時のメールアドレス重複チェックに利用する。
 * JpaRepository<User, Long> を継承することで、save(保存/更新)・findById(主キー検索)・
 * findAll(全件取得)・delete(削除)などの基本的なCRUD操作が自動的に使えるようになる。
 * ジェネリクスの User はエンティティ型、Long は主キー(id)の型を表す。
 */
public interface UserRepository extends JpaRepository<User, Long> {
    // メソッド名からSpring Data JPAが自動でクエリを生成する(クエリメソッド)。
    // 「emailカラムが一致するUserを1件検索する」処理を意味し、
    // 存在しない場合にnullではなくOptional.empty()を返すことでNullPointerExceptionを防ぐ。
    Optional<User> findByEmail(String email);

    // 大文字小文字を区別せずにメールアドレスでユーザーを検索する。
    // ログイン処理・パスワード再設定で使用する。メールアドレスは一般的に大文字小文字を
    // 区別しないため、"Alice@example.com" と "alice@example.com" を同一人物として扱う
    Optional<User> findByEmailIgnoreCase(String email);

    // 「emailカラムが一致するUserが存在するかどうか」をtrue/falseで返すクエリメソッド。
    // 会員登録時にメールアドレスの重複登録を防ぐためのチェックに使用する。
    boolean existsByEmail(String email);

    // 大文字小文字を区別しない重複チェック用。"Alice@example.com" 登録済みの状態で
    // "alice@example.com" を新規登録できてしまう（実質同じアドレスの二重登録）のを防ぐ
    boolean existsByEmailIgnoreCase(String email);

    // 管理画面の会員管理一覧用。全ユーザーを登録日時の降順（新しい順）で取得する。
    List<User> findAllByOrderByCreatedAtDesc();
}
