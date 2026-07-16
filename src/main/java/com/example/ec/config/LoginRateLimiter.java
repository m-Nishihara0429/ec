package com.example.ec.config;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * ログインエンドポイントへの総当たり攻撃（ブルートフォース）対策として、
 * IPアドレスごとの直近のログイン失敗回数を保持するカウンター。
 * 一定時間内の失敗回数がMAX_ATTEMPTSを超えたIPは、LoginRateLimitFilterによって
 * それ以降のログイン試行そのものをブロックされる。
 * インメモリ実装のため、複数インスタンスで水平スケールする場合はRedis等の
 * 共有ストアに置き換える必要がある（学習用途の単一インスタンス運用が前提）。
 */
@Component
public class LoginRateLimiter {

    // この件数以上の失敗が直近のWINDOWの間にあれば、それ以降の試行をブロックする
    private static final int MAX_ATTEMPTS = 5;
    // 失敗回数をカウントする時間枠
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        Deque<Instant> failures = failuresByIp.get(ip);
        if (failures == null) {
            return false;
        }
        removeExpired(failures);
        return failures.size() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String ip) {
        Deque<Instant> failures = failuresByIp.computeIfAbsent(ip, key -> new ConcurrentLinkedDeque<>());
        failures.addLast(Instant.now());
        removeExpired(failures);
    }

    public void recordSuccess(String ip) {
        // 成功したらそのIPの失敗履歴はリセットする（正当な利用者が誤入力を数回した後に成功した場合など）
        failuresByIp.remove(ip);
    }

    private void removeExpired(Deque<Instant> failures) {
        Instant threshold = Instant.now().minus(WINDOW);
        while (true) {
            Instant oldest = failures.peekFirst();
            if (oldest == null || oldest.isAfter(threshold)) {
                break;
            }
            failures.pollFirst();
        }
    }
}
