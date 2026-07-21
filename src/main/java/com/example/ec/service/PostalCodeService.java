package com.example.ec.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 郵便番号から住所を検索するサービス。
 * 外部の郵便番号検索API（zipcloud、登録不要の無料API）をRestTemplateで呼び出し、
 * 都道府県・市区町村・町域を連結した住所文字列を返す。
 * チェックアウト画面の「郵便番号から住所を自動入力する」機能に使われる（OrderController参照）。
 */
@Service
@Slf4j
public class PostalCodeService {

    // zipcloud（日本郵便の郵便番号データを提供する無料API）の検索エンドポイント。{zipcode}はRestTemplateのURL変数展開に使う
    private static final String ZIPCLOUD_URL = "https://zipcloud.ibsnet.co.jp/api/search?zipcode={zipcode}";

    // 郵便番号として妥当な形式（ハイフン除去後、数字7桁）かどうかを判定する正規表現
    private static final Pattern ZIPCODE_PATTERN = Pattern.compile("\\d{7}");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PostalCodeService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 郵便番号から住所（都道府県+市区町村+町域）を検索する。
     *
     * @param rawZipcode 郵便番号（ハイフンあり "100-0001" / なし "1000001" のいずれでも可）
     * @return 都道府県・市区町村・町域を連結した住所文字列
     * @throws IllegalArgumentException 郵便番号の形式が不正、該当住所が無い、または外部APIの呼び出しに失敗した場合
     */
    public String lookupAddress(String rawZipcode) {
        // ハイフンなど数字以外の文字を取り除く（"100-0001" -> "1000001"）。ユーザーがどちらの形式で入力しても受け付けるため
        String zipcode = rawZipcode == null ? "" : rawZipcode.replaceAll("[^0-9]", "");
        if (!ZIPCODE_PATTERN.matcher(zipcode).matches()) {
            throw new IllegalArgumentException("郵便番号は7桁の数字で入力してください");
        }

        // zipcloud APIはJSONを返すにもかかわらずContent-Type: text/plainを付与してくるため、
        // RestTemplateにレスポンス型を直接指定して取得させると「対応するHttpMessageConverterが無い」
        // (UnknownContentTypeException)として失敗する。そのため一旦文字列として受け取り、Jacksonで手動パースする
        String rawJson;
        try {
            rawJson = restTemplate.getForObject(ZIPCLOUD_URL, String.class, zipcode);
        } catch (RestClientException e) {
            // 外部APIのタイムアウト・接続失敗など、こちら側の入力ミスではないエラーはユーザーに手動入力を促す穏当なメッセージに変換する。
            // 原因調査のため、ユーザーには見せない詳細な例外はサーバーログに残す
            log.warn("郵便番号検索APIの呼び出しに失敗しました: zipcode={}", zipcode, e);
            throw new IllegalArgumentException("住所検索に失敗しました。時間をおいて再度お試しいただくか、住所を直接入力してください", e);
        }

        ZipcloudResponse response;
        try {
            response = objectMapper.readValue(rawJson, ZipcloudResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("郵便番号検索APIのレスポンス解析に失敗しました: zipcode={}", zipcode, e);
            throw new IllegalArgumentException("住所検索に失敗しました。時間をおいて再度お試しいただくか、住所を直接入力してください", e);
        }

        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new IllegalArgumentException("該当する住所が見つかりませんでした");
        }

        ZipcloudResult result = response.getResults().get(0);
        return result.getAddress1() + result.getAddress2() + result.getAddress3();
    }

    /**
     * zipcloud APIのレスポンス全体をマッピングするための内部DTO。
     * 使わないフィールド（message, status）はJacksonが無視するよう{@code @JsonIgnoreProperties}を付けている。
     */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ZipcloudResponse {
        private List<ZipcloudResult> results;
    }

    /**
     * zipcloud APIが返す住所1件分（都道府県・市区町村・町域）をマッピングするための内部DTO。
     */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ZipcloudResult {
        private String address1; // 都道府県
        private String address2; // 市区町村
        private String address3; // 町域
    }
}
