package com.example.ec.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 外部API呼び出し（郵便番号検索など）に使うRestTemplateのBean定義。
 * 接続先の外部APIが応答しない場合でもリクエストスレッドが長時間ブロックされ続けないよう、
 * 接続・読み取りとも短めのタイムアウトを設定する。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }
}
