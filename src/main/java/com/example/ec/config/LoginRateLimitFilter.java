package com.example.ec.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * POST /login （ログイン処理）へのリクエストを、IPアドレスごとの直近の失敗回数に基づいて
 * ブルートフォース対策としてレート制限するフィルター。
 * SecurityConfigでUsernamePasswordAuthenticationFilterより前段に組み込むことで、
 * ブロック対象のIPはSpring Securityの認証処理（パスワード照合）に到達する前に429で拒否できる。
 * あえて{@code @Component}にはしていない。付与するとSpring Bootが自動的に「全URL向けの
 * 素のサーブレットフィルター」としても二重登録してしまい、SecurityConfig側のチェーンと合わせて
 * 1リクエストにつき2回実行されてしまうため（SecurityConfigのfilterChain()内でnewして手動配線する）。
 */
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginRateLimiter loginRateLimiter;

    public LoginRateLimitFilter(LoginRateLimiter loginRateLimiter) {
        this.loginRateLimiter = loginRateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(path)) {
            String ip = request.getRemoteAddr();
            if (loginRateLimiter.isBlocked(ip)) {
                log.warn("ログイン試行をレート制限によりブロック: ip={}", ip);
                // Servlet APIの定数一覧(HttpServletResponse.SC_*)には429(Too Many Requests)が存在しないため直値を使う
                response.setStatus(429);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("ログイン試行回数が上限を超えました。しばらく時間をおいて再度お試しください。");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
