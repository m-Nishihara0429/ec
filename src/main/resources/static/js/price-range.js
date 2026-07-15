// ページのDOM構築が完了した後に、価格帯を絞り込むための「デュアルハンドル（左右2つのつまみ）」の
// レンジスライダーを初期化する処理。<input type="range">を2本重ねて使い、見た目上は1本のスライダーに
// 左右の色付きバー（トラック）を表示させる仕組みになっている。
document.addEventListener('DOMContentLoaded', function () {
    // 「最小価格」を選択するための range input要素（下限を動かすつまみ）を取得する。
    var minRange = document.getElementById('minPriceRange');
    // 「最大価格」を選択するための range input要素（上限を動かすつまみ）を取得する。
    var maxRange = document.getElementById('maxPriceRange');
    // 現在選択されている最小価格を表示するラベル（テキスト）要素を取得する。
    var minLabel = document.getElementById('minPriceLabel');
    // 現在選択されている最大価格を表示するラベル（テキスト）要素を取得する。
    var maxLabel = document.getElementById('maxPriceLabel');
    // 2つのつまみの間を塗りつぶして見せる、選択範囲を表すバー（トラックの塗りつぶし部分）の要素を取得する。
    var trackFill = document.getElementById('priceRangeTrackFill');

    // 必要な要素（最小/最大のスライダー、または塗りつぶしバー）がページ内に存在しない場合は、
    // このページでは価格スライダー機能を使わないと判断し、以降の処理を行わずに終了する。
    if (!minRange || !maxRange || !trackFill) {
        return;
    }

    // 数値を「¥12,345」のような日本円表記の文字列に変換するためのヘルパー関数。
    function formatYen(value) {
        // Number()で数値化してから、toLocaleStringで3桁区切りのカンマを入れ、先頭に¥記号を付ける。
        return '¥' + Number(value).toLocaleString('ja-JP');
    }

    // スライダーの値が変わるたびに呼び出され、
    // (1) 塗りつぶしバーの表示位置・幅の再計算、(2) ラベルのテキスト更新、を行う中心的な関数。
    function update() {
        // minRangeのmin属性（スライダー全体で選択できる最小の下限値）を整数として取得する。
        var bound = parseInt(minRange.min, 10);
        // minRangeのmax属性（スライダー全体で選択できる最大の上限値）を整数として取得する。
        // ※min側とmax側のinputは同じmin/max範囲で設定されている前提。
        var boundMax = parseInt(minRange.max, 10);
        // 現在ユーザーが選んでいる「最小価格」のつまみの値を整数として取得する。
        var min = parseInt(minRange.value, 10);
        // 現在ユーザーが選んでいる「最大価格」のつまみの値を整数として取得する。
        var max = parseInt(maxRange.value, 10);
        // スライダー全体の値の幅（上限-下限）を計算する。万が一0になった場合はゼロ除算を防ぐため1を使う。
        var range = boundMax - bound || 1;

        // 「最小価格」のつまみが、スライダー全体の中で左から何%の位置にあるかを計算する。
        var leftPct = ((min - bound) / range) * 100;
        // 「最大価格」のつまみが、スライダー全体の中で左から何%の位置にあるかを計算する。
        var rightPct = ((max - bound) / range) * 100;
        // 塗りつぶしバーの左端を、最小価格つまみの位置(%)に合わせてCSSのleftプロパティで設定する。
        trackFill.style.left = leftPct + '%';
        // 塗りつぶしバーの右端を、最大価格つまみの位置から逆算した値(100%-位置)でCSSのrightプロパティに設定する。
        // これにより、2つのつまみの間だけが塗りつぶされて見えるようになる。
        trackFill.style.right = (100 - rightPct) + '%';

        // 最小価格ラベルのテキストを、日本円表記に整形した現在の最小値で更新する。
        minLabel.textContent = formatYen(min);
        // 最大価格ラベルのテキストを、日本円表記に整形した現在の最大値で更新する。
        maxLabel.textContent = formatYen(max);
    }

    // 「最小価格」のつまみをドラッグ・操作している最中（input イベント）に発火するリスナーを登録する。
    minRange.addEventListener('input', function () {
        // もし最小価格のつまみが最大価格のつまみを追い越して大きくなってしまった場合は、
        // 最小価格の値を最大価格の値に合わせることで、つまみ同士が交差しないように制御する。
        if (parseInt(minRange.value, 10) > parseInt(maxRange.value, 10)) {
            minRange.value = maxRange.value;
        }
        // 値の補正が終わったら、表示（塗りつぶしバーとラベル）を最新の値に合わせて更新する。
        update();
    });

    // 「最大価格」のつまみをドラッグ・操作している最中（input イベント）に発火するリスナーを登録する。
    maxRange.addEventListener('input', function () {
        // もし最大価格のつまみが最小価格のつまみより小さくなってしまった場合は、
        // 最大価格の値を最小価格の値に合わせることで、つまみ同士が交差しないように制御する。
        if (parseInt(maxRange.value, 10) < parseInt(minRange.value, 10)) {
            maxRange.value = minRange.value;
        }
        // 値の補正が終わったら、表示（塗りつぶしバーとラベル）を最新の値に合わせて更新する。
        update();
    });

    // ページ読み込み直後、まだ一度もスライダーを操作していない初期状態でも
    // 塗りつぶしバーとラベルが正しい初期値で表示されるように、最初に一度updateを呼んでおく。
    update();
});
