package com.example.ec.util;

import java.text.Normalizer;

/**
 * 文字列正規化のための低レベルユーティリティ。
 * NFKC正規化(全角/半角統一)とひらがな⇔カタカナ変換を提供し、
 * 商品検索のキーワードバリエーション生成(ProductSpecifications)の土台となる。
 */
public final class TextNormalizer {

    // ひらがな範囲の開始文字（'ぁ'）。この文字コード以上ならひらがなとみなす
    private static final char HIRAGANA_START = 'ぁ';
    // ひらがな範囲の終了文字（'ゖ'）。この文字コード以下ならひらがなとみなす
    private static final char HIRAGANA_END = 'ゖ';
    // カタカナ範囲の開始文字（'ァ'）。この文字コード以上ならカタカナとみなす
    private static final char KATAKANA_START = 'ァ';
    // ひらがなとカタカナの文字コードの差分（Unicode上、カタカナはひらがなより0x60大きい）
    private static final int KATAKANA_OFFSET = 0x60;

    /** インスタンス化を禁止するためのprivateコンストラクタ（staticメソッドのみを提供するユーティリティクラス）。 */
    private TextNormalizer() {
    }

    /** 全角英数・半角カナなどを標準形に正規化する(NFKC)。 */
    public static String normalizeWidth(String text) {
        // textがnullならnullを返し、そうでなければUnicodeのNFKC正規化（全角/半角・互換文字の統一）を適用する
        return text == null ? null : Normalizer.normalize(text, Normalizer.Form.NFKC);
    }

    /** ひらがなの各文字コードにオフセットを加算し、対応するカタカナへ変換する。 */
    public static String toKatakana(String text) {
        // 変換後の文字列を組み立てるためのバッファ（元の文字列と同じ長さを初期容量に確保）
        StringBuilder sb = new StringBuilder(text.length());
        // 文字列を先頭から1文字ずつ走査する
        for (int i = 0; i < text.length(); i++) {
            // i番目の文字を取得
            char c = text.charAt(i);
            // ひらがな範囲内の文字ならオフセットを加算してカタカナに変換し、範囲外ならそのまま追加する
            sb.append(c >= HIRAGANA_START && c <= HIRAGANA_END ? (char) (c + KATAKANA_OFFSET) : c);
        }
        // 組み立てた文字列を返す
        return sb.toString();
    }

    /** カタカナの各文字コードからオフセットを減算し、対応するひらがなへ変換する(toKatakanaの逆変換)。 */
    public static String toHiragana(String text) {
        // 変換後の文字列を組み立てるためのバッファ（元の文字列と同じ長さを初期容量に確保）
        StringBuilder sb = new StringBuilder(text.length());
        // 文字列を先頭から1文字ずつ走査する
        for (int i = 0; i < text.length(); i++) {
            // i番目の文字を取得
            char c = text.charAt(i);
            // カタカナ範囲の終端文字コード（ひらがな終端 + オフセット）をその都度計算する
            char katakanaEnd = (char) (HIRAGANA_END + KATAKANA_OFFSET);
            // カタカナ範囲内の文字ならオフセットを減算してひらがなに変換し、範囲外ならそのまま追加する
            sb.append(c >= KATAKANA_START && c <= katakanaEnd ? (char) (c - KATAKANA_OFFSET) : c);
        }
        // 組み立てた文字列を返す
        return sb.toString();
    }
}
