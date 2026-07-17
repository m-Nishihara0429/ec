package com.example.ec.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * POST /login （ログイン処理）へのリクエストを、IPアドレスごとの直近の失敗回数に基づいて
 * ブルートフォース対策としてレート制限するフィルター。
 * SecurityConfigでUsernamePasswordAuthenticationFilterより前段に組み込むことで、
 * ブロック対象のIPはSpring Securityの認証処理（パスワード照合）に到達する前に429で拒否できる。
 * あえて{@code @Component}にはしていない。付与するとSpring Bootが自動的に「全URL向けの
 * 素のサーブレットフィルター」としても二重登録してしまい、SecurityConfig側のチェーンと合わせて
 * 1リクエストにつき2回実行されてしまうため（SecurityConfigのfilterChain()内でnewして手動配線する）。
 *
 * <p>POST /register・POST /forgot-password も同様にボット・スパムによる大量送信の対象になりうるため、
 * ここで併せてレート制限する。こちらはSpring Securityの認証イベントを経由しないため
 * （SecurityAuditListenerの対象外）、ログインのような「成功でリセットする失敗カウンター」ではなく、
 * 成否を問わず送信そのものを1回の試行としてカウントするシンプルな方式にしている。
 * ログインとキーの名前空間を分ける（パス名を前置する）ことで、同一IPからの
 * ログイン試行回数と会員登録・パスワード再設定申請の回数が互いに影響しないようにしている。</p>
 */
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    // ログインと異なり成功シグナルが無いため、送信そのものを試行としてカウントする対象パス
    private static final Set<String> ATTEMPT_LIMITED_PATHS = Set.of("/register", "/forgot-password");

    private final LoginRateLimiter loginRateLimiter;

    public LoginRateLimitFilter(LoginRateLimiter loginRateLimiter) {
        this.loginRateLimiter = loginRateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String ip = request.getRemoteAddr();

        if ("/login".equals(path)) {
            if (loginRateLimiter.isBlocked(ip)) {
                log.warn("ログイン試行をレート制限によりブロック: ip={}", ip);
                reject(response, "ログイン試行回数が上限を超えました。しばらく時間をおいて再度お試しください。");
                return;
            }
        } else if (ATTEMPT_LIMITED_PATHS.contains(path)) {
            // ログインとは別の名前空間のキー（例: "/register:203.0.113.1"）で試行回数を数える
            String key = path + ":" + ip;
            if (loginRateLimiter.isBlocked(key)) {
                log.warn("送信をレート制限によりブロック: path={}, ip={}", path, ip);
                reject(response, "送信回数が上限を超えました。しばらく時間をおいて再度お試しください。");
                return;
            }
            // 成否を問わず、送信そのものを1回の試行としてカウントする（recordSuccessは呼ばない）
            loginRateLimiter.recordFailure(key);
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        // Servlet APIの定数一覧(HttpServletResponse.SC_*)には429(Too Many Requests)が存在しないため直値を使う
        response.setStatus(429);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
