package com.example.ec.config;

import com.example.ec.entity.User;
import com.example.ec.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Securityがログイン時にユーザー情報を取得するためのサービス。
 * メールアドレスをユーザー名（username）として扱い、DBから該当ユーザーを検索する。
 * SecurityConfigのformLoginやrememberMeから、認証処理の中で自動的に呼び出される。
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    // ユーザー情報をDBから検索するためのリポジトリ
    private final UserRepository userRepository;

    /**
     * コンストラクタ。Spring DIによってUserRepositoryが自動的に注入される。
     * @param userRepository ユーザーDBアクセス用リポジトリ
     */
    public CustomUserDetailsService(UserRepository userRepository) {
        // フィールドに保持する
        this.userRepository = userRepository;
    }

    /**
     * ユーザー名（このアプリではメールアドレス）からユーザー情報を読み込む。
     * Spring Securityの認証処理から呼び出され、返り値のUserDetailsを使って
     * パスワード照合や権限確認が行われる。
     * @param email ログインフォームに入力されたメールアドレス（username扱い）
     * @return Spring Security用にラップしたユーザー情報
     * @throws UsernameNotFoundException 該当ユーザーが存在しない場合
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // ログインフォームに入力されたメールアドレスでユーザーを検索し、
        // 見つからない場合は認証失敗として例外を投げる
        User user = userRepository.findByEmail(email)
                // Optionalが空（該当ユーザーなし）の場合はUsernameNotFoundExceptionを送出する
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません: " + email));
        // 取得したUserエンティティをSpring Security用のUserDetailsにラップして返す
        return new SecurityUserDetails(user);
    }
}
