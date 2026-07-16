package com.example.ec.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ログインのブルートフォース対策カウンター（LoginRateLimiter）の単体テスト。
 */
class LoginRateLimiterTest {

    @Test
    void 失敗回数が上限未満の間はブロックされない() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("203.0.113.1");
        }
        assertThat(limiter.isBlocked("203.0.113.1")).isFalse();
    }

    @Test
    void 失敗回数が上限に達するとブロックされる() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("203.0.113.2");
        }
        assertThat(limiter.isBlocked("203.0.113.2")).isTrue();
    }

    @Test
    void 別のIPアドレスの失敗回数には影響しない() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("203.0.113.3");
        }
        assertThat(limiter.isBlocked("203.0.113.4")).isFalse();
    }

    @Test
    void 成功を記録するとそのIPの失敗履歴はリセットされる() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("203.0.113.5");
        }
        assertThat(limiter.isBlocked("203.0.113.5")).isTrue();

        limiter.recordSuccess("203.0.113.5");

        assertThat(limiter.isBlocked("203.0.113.5")).isFalse();
    }
}
