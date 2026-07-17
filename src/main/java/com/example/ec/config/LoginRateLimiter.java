package com.example.ec.config;

import org.springframework.scheduling.annotation.Scheduled;
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
 * キーは呼び出し側が自由に決められる文字列（例: 素のIPアドレス、または"register:"+IPのような
 * 用途別の名前空間付きIP）であり、このクラス自体はログイン専用ではなく汎用的な
 * 「キーごとの直近の試行回数」カウンターとして、会員登録・パスワード再設定申請の
 * レート制限（LoginRateLimitFilter参照）にも流用している。
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
        removeExpired(ip, failures);
        return failures.size() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String ip) {
        Deque<Instant> failures = failuresByIp.computeIfAbsent(ip, key -> new ConcurrentLinkedDeque<>());
        failures.addLast(Instant.now());
        removeExpired(ip, failures);
    }

    public void recordSuccess(String ip) {
        // 成功したらそのIPの失敗履歴はリセットする（正当な利用者が誤入力を数回した後に成功した場合など）
        failuresByIp.remove(ip);
    }

    private void removeExpired(String ip, Deque<Instant> failures) {
        Instant threshold = Instant.now().minus(WINDOW);
        while (true) {
            Instant oldest = failures.peekFirst();
            if (oldest == null || oldest.isAfter(threshold)) {
                break;
            }
            failures.pollFirst();
        }
        // 失敗履歴が空になったIPのエントリ自体もMapから取り除く。これを省くと、二度とアクセスしてこない
        // IP（1回失敗しただけで以後現れない、スキャン等でIPを変え続ける攻撃者を含む）のエントリが
        // 空のDequeのままいつまでもMapに残り続け、メモリリークになる。
        if (failures.isEmpty()) {
            failuresByIp.remove(ip, failures);
        }
    }

    /**
     * 全IPを対象に、期限切れの失敗履歴・空になったエントリを一掃する定期処理。
     * isBlocked/recordFailureは「アクセスしてきたIPだけ」をその都度掃除するため、
     * 一度失敗しただけで二度とアクセスしてこないIP（スキャン等でIPを変え続ける攻撃者を含む）の
     * エントリはいつまでも掃除されない。このスケジュール処理がMap全体を定期的に巡回することで、
     * そうした「誰も触らないエントリ」による長期的なメモリ増加を防ぐ。
     */
    @Scheduled(fixedRate = 15, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void cleanupExpiredEntries() {
        for (String ip : failuresByIp.keySet()) {
            Deque<Instant> failures = failuresByIp.get(ip);
            if (failures != null) {
                removeExpired(ip, failures);
            }
        }
    }
}
