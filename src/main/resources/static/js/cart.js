// ページのHTMLがすべて読み込まれ、DOM構築が完了したタイミングで実行される処理を登録する。
// カート画面や商品詳細画面にある「数量」入力欄の値をチェックし、不正な値を自動的に補正する役割を持つ。
document.addEventListener('DOMContentLoaded', function () {
    // name="quantity" を持つ入力要素（数量入力ボックス）をページ内からすべて取得し、
    // それぞれに対して同じチェック処理を仕込むためにループする。
    document.querySelectorAll('input[name="quantity"]').forEach(function (input) {
        // 1つの数量入力欄（input）に対して、「値が変更されて確定した（フォーカスが外れた等）」タイミングで
        // 発火する change イベントのリスナーを登録する。
        input.addEventListener('change', function () {
            // input要素のmin属性（許容される最小値）を取得する。
            // min属性が設定されていない場合は文字列 '0' を使い、10進数の整数に変換する。
            var min = parseInt(input.getAttribute('min') || '0', 10);
            // 入力値が数値として解釈できない場合(isNaN)、または
            // 入力値を10進整数に変換した結果がmin未満だった場合は不正な入力とみなす。
            if (isNaN(input.value) || parseInt(input.value, 10) < min) {
                // 不正な値だった場合は、入力欄の値を最小値minに強制的に書き換えて補正する。
                input.value = min;
            }
        });
    });
});
