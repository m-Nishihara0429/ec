package com.example.ec.config;

import com.example.ec.entity.Role;
import com.example.ec.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Securityが扱う認証情報と、アプリ独自の{@link User}エンティティを橋渡しするクラス。
 * ログイン中のユーザー情報（権限・メールアドレスなど）をSecurity側に提供する。
 * Spring Securityの各種処理（認可判定・セッション保持など）は
 * このUserDetailsインターフェースを通じてユーザー情報を参照する。
 */
public class SecurityUserDetails implements UserDetails {

    // ラップ対象のアプリ独自ユーザーエンティティ（DBから取得したユーザー本体）
    private final User user;

    /**
     * コンストラクタ。
     * @param user ラップするアプリ独自のユーザーエンティティ
     */
    public SecurityUserDetails(User user) {
        // フィールドにそのまま保持する
        this.user = user;
    }

    /**
     * ラップしているアプリ独自のUserエンティティを取得する。
     * コントローラー側でログインユーザーのID・名前などにアクセスする際に使う。
     * @return ラップ元のUserエンティティ
     */
    public User getUser() {
        // 保持しているuserフィールドをそのまま返す
        return user;
    }

    /**
     * このユーザーが持つ権限（ロール）一覧を返す。
     * Spring Securityの認可処理（hasRole等）はここで返した権限を見て判定する。
     * ROLE_MASTERのユーザーには、管理者向け画面（hasRole("ADMIN")で保護されたURL）にも
     * そのままアクセスできるよう、ROLE_MASTERに加えてROLE_ADMINの権限もあわせて付与する
     * （マスター管理者は管理者の操作を包含する上位ロールという位置づけのため）。
     * @return 権限のコレクション
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (user.getRole() == Role.ROLE_MASTER) {
            // マスター管理者は「マスター管理者専用機能」と「管理者向け機能」の両方にアクセスできる必要があるため
            return List.of(new SimpleGrantedAuthority(Role.ROLE_MASTER.name()),
                    new SimpleGrantedAuthority(Role.ROLE_ADMIN.name()));
        }
        // それ以外（ROLE_ADMIN / ROLE_USER）は、そのロールをそのまま権限として1つ付与する
        return List.of(new SimpleGrantedAuthority(user.getRole().name()));
    }

    /**
     * 認証に使うパスワード（ハッシュ化済み）を返す。
     * @return DBに保存されているハッシュ化パスワード
     */
    @Override
    public String getPassword() {
        // Userエンティティが保持するハッシュ化済みパスワードをそのまま返す
        return user.getPassword();
    }

    /**
     * Spring Securityが「ユーザー名」として扱う値を返す。
     * このアプリではメールアドレスをログインIDとして使うため、メールアドレスを返す。
     * @return ユーザーのメールアドレス
     */
    @Override
    public String getUsername() {
        // ログインIDとしてメールアドレスを利用しているため、それを返す
        return user.getEmail();
    }

    /**
     * アカウントの有効期限が切れていないかを返す。
     * @return 期限切れでなければtrue
     */
    @Override
    public boolean isAccountNonExpired() {
        // このアプリではアカウント有効期限の概念がないため常にtrue（期限切れなし）を返す
        return true;
    }

    /**
     * アカウントがロックされていないかを返す。
     * @return ロックされていなければtrue
     */
    @Override
    public boolean isAccountNonLocked() {
        // このアプリではアカウントロック機能がないため常にtrue（ロックなし）を返す
        return true;
    }

    /**
     * 認証情報（パスワード）の有効期限が切れていないかを返す。
     * @return 期限切れでなければtrue
     */
    @Override
    public boolean isCredentialsNonExpired() {
        // パスワードの有効期限管理はしていないため常にtrue（期限切れなし）を返す
        return true;
    }

    /**
     * アカウントが有効化されているかを返す。
     * マスター管理者が会員を無効化した場合、ここがfalseになりログインできなくなる。
     * @return 有効であればtrue
     */
    @Override
    public boolean isEnabled() {
        // Userエンティティのenabledフラグをそのまま返す
        return user.isEnabled();
    }
}
