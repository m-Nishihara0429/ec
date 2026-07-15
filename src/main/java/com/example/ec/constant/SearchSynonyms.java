package com.example.ec.constant;

import java.util.List;
import java.util.Set;

/**
 * 商品検索で同一視する語のグループ。表記はカタカナに正規化した状態で比較する。
 * 例えば「PC」で検索しても「パソコン」で検索しても同じ商品がヒットするように、
 * 同義語をグループにまとめて管理する辞書クラス。
 */
public final class SearchSynonyms {

    // 同義語グループ一覧。例えば「水筒」で検索すると「タンブラー」「マグ」も対象になるため、
    // 「水筒」で「ステンレスタンブラー」のような商品もヒットする。
    // 各Setが1つの同義語グループを表し、グループ内のどの語で検索してもグループ全体が検索対象になる。
    private static final List<Set<String>> GROUPS = List.of(
            // 「PC」と「パソコン」は同義語として扱う
            Set.of("PC", "パソコン"),
            // 「イヤホン」「ヘッドホン」「イヤフォン」は同義語として扱う
            Set.of("イヤホン", "ヘッドホン", "イヤフォン"),
            // 「ケトル」「ヤカン」は同義語として扱う
            Set.of("ケトル", "ヤカン"),
            // 「タンブラー」「水筒」「マグ」は同義語として扱う
            Set.of("タンブラー", "水筒", "マグ"),
            // 「本」「書籍」は同義語として扱う
            Set.of("本", "書籍")
    );

    /** インスタンス化を禁止するためのprivateコンストラクタ（定数・staticメソッドのみを提供するクラス）。 */
    private SearchSynonyms() {
    }

    /**
     * keyword と同義語グループを共有する語(自分自身を含む)を返す。一致するグループがなければ keyword のみを返す。
     * @param keyword 検索キーワード（比較前に呼び出し側でカタカナ等に正規化されている想定）
     * @return keywordを含む同義語グループ。該当グループがない場合はkeyword単体のSet
     */
    public static Set<String> expand(String keyword) {
        // 定義済みの同義語グループを1つずつ確認する
        for (Set<String> group : GROUPS) {
            // グループ内の各語を1つずつ確認する
            for (String term : group) {
                // 大文字小文字を無視してkeywordと一致するか判定する
                if (term.equalsIgnoreCase(keyword)) {
                    // 一致したら、そのグループ全体（同義語すべて）を返す
                    return group;
                }
            }
        }
        // どのグループにも該当しなければ、keyword自身のみを含むSetを返す
        return Set.of(keyword);
    }
}
