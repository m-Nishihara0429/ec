package com.example.ec.repository;

import com.example.ec.entity.PasswordResetToken;
import com.example.ec.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * パスワード再設定用トークンを管理するリポジトリ。
 * 「パスワードを忘れた場合」の再設定メールに含めるトークンの発行・検証に使う。
 * JpaRepository<PasswordResetToken, Long> を継承することで、
 * save・findById・findAll・deleteなどの基本的なCRUD操作が自動的に使えるようになる。
 * PasswordResetToken はエンティティ型、Long は主キー(id)の型を表す。
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    // 「tokenカラムが一致するPasswordResetTokenを1件検索する」クエリメソッド。
    // 再設定メール内のリンクに含まれるトークン文字列から、対応するレコード
    // (有効期限や対象ユーザー)を引き当てて検証するために使用する。
    Optional<PasswordResetToken> findByToken(String token);
    // 新しい再設定トークンを発行する前に、同一ユーザーの古いトークンを削除するために使用する
    // 「userカラムが一致するPasswordResetTokenを全件削除する」クエリメソッド(戻り値なし)。
    void deleteByUser(User user);
}
