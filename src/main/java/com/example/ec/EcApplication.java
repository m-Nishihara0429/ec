package com.example.ec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * このECサイトアプリケーションのエントリーポイント。
 * {@code @SpringBootApplication} により自動設定・コンポーネントスキャンが有効になる。
 * このアノテーションは {@code @Configuration}・{@code @EnableAutoConfiguration}・
 * {@code @ComponentScan} をまとめたもので、com.example.ec配下のコンポーネントが
 * 自動的に検出・登録される。
 * {@code @EnableCaching} はCategoryServiceのキャッシュ({@code @Cacheable}/{@code @CacheEvict})を、
 * {@code @EnableScheduling} はLoginRateLimiterの期限切れIP定期クリーンアップを有効化する。
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class EcApplication {

    /**
     * アプリケーションの起動メソッド（Javaプログラムの標準的なエントリーポイント）。
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        // Spring Bootアプリケーションを起動する（組み込みTomcatの起動、DI コンテナの構築などを行う）
        SpringApplication.run(EcApplication.class, args);
    }
}
