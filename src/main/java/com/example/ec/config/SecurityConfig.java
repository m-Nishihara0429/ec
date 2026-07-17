package com.example.ec.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Securityの設定クラス。認証・認可のルール、ログイン/ログアウト、
 * remember-me（ログイン状態の保持）などWebセキュリティ全般をここで構成する。
 * このクラス全体が「誰が・どのURLに・どんな条件でアクセスできるか」というアプリの
 * セキュリティポリシーを定義する中核部分であり、initやログイン画面もここで結び付けられる。
 */
@Configuration // このクラスがSpringの設定クラス（Beanを定義するクラス）であることを示す
@EnableWebSecurity // Spring SecurityのWebセキュリティ機能を有効化する（デフォルトのセキュリティ自動設定をこの設定で置き換える）
public class SecurityConfig {

    // remember-meクッキーの有効期間（秒）。60秒×60分×24時間×7日 = 1週間を表す
    private static final int REMEMBER_ME_VALIDITY_SECONDS = 60 * 60 * 24 * 7; // 1週間

    // application.propertiesに書かれているローカル開発用のプレースホルダー値。
    // 本番相当のpostgresプロファイルでこの値のまま起動しようとした場合、
    // 環境変数REMEMBER_ME_KEYの設定を忘れている（render.yamlのsync:falseコメントで
    // 警告している設定を怠った）とみなしfail-fastする
    private static final String DEFAULT_REMEMBER_ME_KEY = "ec-site-remember-me-key";

    // ログイン時にユーザー情報を取得するためのサービス（rememberMeのuserDetailsServiceとしても使用）
    private final CustomUserDetailsService userDetailsService;
    // 現在有効なプロファイルを判定するために使用する（postgresプロファイル＝本番相当かどうかの判定用）
    private final Environment environment;
    // ログインへのブルートフォース攻撃対策として、IPごとの失敗回数を保持するカウンター
    // （LoginRateLimitFilter自体は@Componentにしていないため、ここでインスタンス化して使う）
    private final LoginRateLimiter loginRateLimiter;

    // remember-meトークンの署名に使う鍵。application.propertiesの設定値から読み込む。
    // アプリ起動のたびにランダムな鍵を生成すると、再起動するだけで
    // 既存のremember-meクッキーが全て無効になってしまうため、
    // 再起動をまたいでもクッキーが有効であり続けるように固定の設定値を使用している。
    @Value("${app.remember-me.key}") // application.propertiesの app.remember-me.key の値をこのフィールドに注入する
    private String rememberMeKey;

    /**
     * コンストラクタ。Spring DIによってCustomUserDetailsServiceとEnvironmentが自動的に注入される。
     * @param userDetailsService ログイン時のユーザー情報取得サービス
     * @param environment        有効なプロファイルを判定するためのSpring標準コンポーネント
     */
    public SecurityConfig(CustomUserDetailsService userDetailsService, Environment environment,
                           LoginRateLimiter loginRateLimiter) {
        // フィールドに保持する
        this.userDetailsService = userDetailsService;
        this.environment = environment;
        this.loginRateLimiter = loginRateLimiter;
    }

    /**
     * 本番相当のpostgresプロファイルで、remember-meキーがローカル開発用のプレースホルダー値の
     * ままになっていないかを起動時に検証する。公開リポジトリに含まれるこのデフォルト値のまま
     * 本番運用すると、remember-meトークンの署名鍵が誰でも知っている値になってしまうため、
     * render.yamlが要求している「デプロイ前に必ずREMEMBER_ME_KEYを個別設定する」運用を
     * 起動失敗という形で強制する（ローカル開発（defaultプロファイル）では従来通り動作する）。
     */
    @PostConstruct
    public void validateRememberMeKey() {
        if (environment.matchesProfiles("postgres") && DEFAULT_REMEMBER_ME_KEY.equals(rememberMeKey)) {
            throw new IllegalStateException(
                    "本番プロファイル(postgres)では環境変数REMEMBER_ME_KEYに強力な値を設定してください。"
                            + "デフォルト値のままだとremember-meトークンの署名鍵が公開リポジトリの値と同じになり危険です。");
        }
    }

    /**
     * パスワードのハッシュ化・照合に使うエンコーダーをBeanとして登録する。
     * DataInitializerやユーザー登録処理でパスワードを保存する際、また
     * ログイン時にSpring Securityが入力パスワードと保存済みハッシュを照合する際に使われる。
     * @return BCryptアルゴリズムを使うPasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt（ソルト付きの一方向ハッシュ関数）でパスワードをハッシュ化するエンコーダーを返す
        return new BCryptPasswordEncoder();
    }

    /**
     * URLごとのアクセス制御、ログイン/ログアウト、remember-meをまとめて設定するフィルターチェーン。
     * Spring SecurityはHTTPリクエストをこのフィルターチェーンに通し、ここで定義したルールに従って
     * 認証・認可の判定やログイン/ログアウト処理を行う。
     * @param http HttpSecurityビルダー（Spring Securityが注入する設定用オブジェクト）
     * @return 構築されたSecurityFilterChain
     * @throws Exception 設定構築時に例外が発生した場合
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // URLパスごとに「誰がアクセスできるか」（認可ルール）を設定するブロック
            .authorizeHttpRequests((AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) -> auth
                // レビュー投稿はログイン済み（一般ユーザー・管理者）のみ許可
                // POST /products/{商品ID}/reviews に対して、USERまたはADMINロールを持つ場合のみ許可する
                .requestMatchers(HttpMethod.POST, "/products/*/reviews").hasAnyRole("USER", "ADMIN")
                // トップページ・商品閲覧・静的リソース・会員登録/ログイン関連は未ログインでも閲覧可
                // permitAll()により、認証なし（未ログイン状態）でも以下のパスにはアクセスできる
                .requestMatchers("/", "/products/**", "/css/**", "/js/**", "/img/**",
                        "/register", "/login", "/error",
                        "/forgot-password", "/reset-password",
                        "/faq", "/contact").permitAll()
                // Actuatorのヘルスチェックのみ、PaaS（Render等）の死活監視のため未ログインでも許可する
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // metrics・info等その他のActuatorエンドポイントは内部情報を含むためADMIN権限のみ許可する
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // 会員（ユーザー）管理はMASTERロールのみアクセス可。
                // より広い範囲にマッチする「/admin/**」ルールより前に置く必要がある
                // （authorizeHttpRequestsは上から順に最初にマッチしたルールが適用されるため）。
                .requestMatchers("/admin/users/**").hasRole("MASTER")
                // 管理画面はADMINロールのみアクセス可
                // /admin/配下のすべてのパスは、ROLE_ADMINを持つユーザーだけがアクセスできる
                // （ROLE_MASTERのユーザーはSecurityUserDetails側でROLE_ADMIN権限も付与されるためここも通過できる）
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // マイページ・カート・購入手続きはログイン済みユーザーのみ
                // /mypage/, /cart/, /checkout/配下は、USERまたはADMINロールを持つログイン済みユーザーのみアクセスできる
                .requestMatchers("/mypage/**", "/cart/**", "/checkout/**").hasAnyRole("USER", "ADMIN")
                // 上記以外は基本的に認証必須
                // ここまでのどのルールにも一致しないリクエストは、ログイン済み（authenticated）であることを要求する
                .anyRequest().authenticated()
            )
            // フォームベースのログイン機能を設定するブロック
            .formLogin(form -> form
                // ログインフォームを表示するページのURL（未認証で保護ページにアクセスするとここへリダイレクトされる）
                .loginPage("/login")
                // ログインフォームがPOST送信される先のURL（Spring Securityがここでユーザー名/パスワードを検証する）
                .loginProcessingUrl("/login")
                // ログイン成功時のリダイレクト先。第2引数falseは「元々アクセスしようとしていたURLがあればそちらを優先する」という意味
                .defaultSuccessUrl("/", false)
                // ログイン失敗時のリダイレクト先（?errorパラメータを付けてログイン画面にエラー表示させる）
                .failureUrl("/login?error")
                // ログインページ自体・ログイン処理URLへのアクセスは未認証でも許可する
                .permitAll()
            )
            // ログアウト機能を設定するブロック
            .logout(logout -> logout
                // ログアウトを実行するURL（ここにアクセスするとセッション破棄などのログアウト処理が行われる）
                .logoutUrl("/logout")
                // ログアウト成功後のリダイレクト先（トップページ）
                .logoutSuccessUrl("/")
                // ログアウトURLへのアクセスは未認証でも許可する（誰でもログアウト操作を実行できる）
                .permitAll()
            )
            // remember-me（ログイン状態を一定期間保持する「ログインを記憶する」機能）の設定。
            // keyには固定の設定値を使い、有効期限は約1週間（REMEMBER_ME_VALIDITY_SECONDS）としている。
            .rememberMe(rememberMe -> rememberMe
                // remember-meトークンの署名に使う鍵。固定値を使うことで再起動後もクッキーの有効性を保つ
                .key(rememberMeKey)
                // remember-meトークンからユーザーを復元する際に使うUserDetailsService
                .userDetailsService(userDetailsService)
                // remember-meクッキーの有効期間（秒）。REMEMBER_ME_VALIDITY_SECONDS（1週間）を指定
                .tokenValiditySeconds(REMEMBER_ME_VALIDITY_SECONDS)
            )
            // ログインへのブルートフォース対策フィルターを、実際の認証処理(UsernamePasswordAuthenticationFilter)
            // より前段に挟むことで、ブロック対象IPはパスワード照合に到達する前に429で拒否できる
            .addFilterBefore(new LoginRateLimitFilter(loginRateLimiter), UsernamePasswordAuthenticationFilter.class)
            // Content-Security-Policy: 全テンプレートが外部CDNやインラインscript/styleを使わず
            // 自ドメインの静的リソース(/css, /js)のみを参照している構成のため、
            // default-srcを'self'に絞り、XSSでの外部スクリプト読み込み・データ持ち出しの影響を減らす。
            // ただしimg-srcは'self'に絞らずhttps:も許可している。商品管理フォーム(admin/product_form.html)は
            // product.imageUrlに管理者が任意の外部URL（https://...）を入力できる設計であり、
            // 'self'のみに絞ると管理者が設定した外部画像URLがブラウザ側で読み込みブロックされ、
            // 「画像URLを設定しているのに表示されない」という実害が出るため。
            // Spring Securityが既定で付与するHSTS・X-Content-Type-Options等のヘッダーはそのまま活かす
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; img-src 'self' data: https:; object-src 'none'; frame-ancestors 'none'")
                )
            );

        // ここまでの設定を反映したSecurityFilterChainを構築して返す
        return http.build();
    }
}
