package com.example.ec.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * ログイン成否をログに記録し、失敗回数をLoginRateLimiterに反映するリスナー。
 * Spring Securityは認証成功/失敗のたびに{@link AuthenticationSuccessEvent}/
 * {@link AbstractAuthenticationFailureEvent}をアプリケーションイベントとして発行するため、
 * それを購読するだけでSecurityConfig側のフィルターチェーンには手を入れずに監査ログ・レート制限を実現できる。
 * これらのイベントはremember-meによる自動ログイン（毎リクエストで再認証が走りうる）でも発行されるため、
 * フォームからの明示的なログイン試行（UsernamePasswordAuthenticationToken）のみを対象とし、
 * 期限切れ・不正なremember-meクッキーによる自動再認証の失敗が、ログイン試行として誤ってカウントされ
 * レート制限やログイン成否ログに紛れ込まないようにしている。
 */
@Component
@Slf4j
public class SecurityAuditListener {

    private final LoginRateLimiter loginRateLimiter;

    public SecurityAuditListener(LoginRateLimiter loginRateLimiter) {
        this.loginRateLimiter = loginRateLimiter;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        String ip = remoteAddress(event.getAuthentication());
        log.info("ログイン成功: user={}, ip={}", event.getAuthentication().getName(), ip);
        loginRateLimiter.recordSuccess(ip);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        String ip = remoteAddress(event.getAuthentication());
        log.warn("ログイン失敗: user={}, ip={}, reason={}", event.getAuthentication().getPrincipal(),
                ip, event.getException().getMessage());
        loginRateLimiter.recordFailure(ip);
    }

    // ブルートフォース攻撃の調査に使えるよう、送信元IPをWebAuthenticationDetailsから取り出す
    private String remoteAddress(Authentication authentication) {
        if (authentication.getDetails() instanceof WebAuthenticationDetails details) {
            return details.getRemoteAddress();
        }
        return "unknown";
    }
}
