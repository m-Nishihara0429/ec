package com.example.ec.dto;

/**
 * 商品一覧画面の並び替え条件を表す列挙型。
 * 画面のプルダウン等で選択され、商品検索クエリの並び順に反映される。
 */
public enum ProductSort {
    // 登録日時が新しい順（デフォルトの並び順）
    // 定数名 NEWEST に対応する画面表示用ラベル文字列として"新着順"を保持する
    NEWEST("新着順"),
    // 価格が安い順（昇順）
    // 定数名 PRICE_ASC に対応する画面表示用ラベル文字列として"価格が安い順"を保持する
    PRICE_ASC("価格が安い順"),
    // 価格が高い順（降順）
    // 定数名 PRICE_DESC に対応する画面表示用ラベル文字列として"価格が高い順"を保持する
    PRICE_DESC("価格が高い順"),
    // 平均評価が高い順（降順）
    // 定数名 RATING_DESC に対応する画面表示用ラベル文字列として"評価が高い順"を保持する
    RATING_DESC("評価が高い順");

    // 各列挙定数が保持する、画面のプルダウン等に表示するための日本語ラベル
    private final String label;

    // コンストラクタ。列挙定数を定義する際(例: NEWEST("新着順"))に渡された
    // 文字列をlabelフィールドに設定する。enumのコンストラクタは暗黙的にprivate。
    ProductSort(String label) {
        this.label = label;
    }

    // labelフィールドの値を返すgetterメソッド。画面側でこの値を使って
    // プルダウンの選択肢テキストなどを表示する。
    public String getLabel() {
        return label;
    }
}
