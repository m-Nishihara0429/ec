// チェックアウト画面で「住所を検索」ボタンを押したときに、入力された郵便番号から
// 住所を自動入力する処理。実際の郵便番号検索（外部APIへの問い合わせ）はサーバー側の
// OrderController#postalCode（PostalCodeService経由でzipcloud APIを呼ぶ）が行い、
// このスクリプトはその結果をfetchで受け取って配送先住所欄に反映するだけの役割を持つ。
document.addEventListener('DOMContentLoaded', function () {
    // 「住所を検索」ボタンを取得する。チェックアウト画面以外ではこの要素が無いため、その場合は何もしない
    var searchBtn = document.getElementById('zipcode-search-btn');
    if (!searchBtn) {
        return;
    }

    // 郵便番号の入力欄、検索結果を反映する配送先住所欄、エラーメッセージ表示欄をそれぞれ取得する
    var zipcodeInput = document.getElementById('zipcode');
    var addressField = document.getElementById('address');
    var errorEl = document.getElementById('zipcode-error');

    searchBtn.addEventListener('click', function () {
        // 前回検索時のエラー表示が残っていれば消しておく
        errorEl.style.display = 'none';
        errorEl.textContent = '';

        var zipcode = zipcodeInput.value.trim();
        if (!zipcode) {
            errorEl.textContent = '郵便番号を入力してください';
            errorEl.style.display = 'block';
            return;
        }

        // ボタンの連打で何度も検索が走らないよう、通信中は無効化しておく
        searchBtn.disabled = true;

        fetch('/checkout/postal-code/' + encodeURIComponent(zipcode))
            .then(function (res) {
                // レスポンスボディ(JSON)を読みつつ、ステータスが200番台だったかどうかも一緒に持ち回る
                return res.json().then(function (data) {
                    return { ok: res.ok, data: data };
                });
            })
            .then(function (result) {
                if (result.ok) {
                    // 検索成功。配送先住所欄に結果を反映する（番地・建物名などは検索結果に含まれないため、
                    // ユーザーが続きを手入力する前提の値としてそのままセットする）
                    addressField.value = result.data.address;
                } else {
                    // 郵便番号の形式不正・該当なし・外部API失敗など、サーバー側から返されたエラーメッセージを表示する
                    errorEl.textContent = result.data.error || '住所が見つかりませんでした';
                    errorEl.style.display = 'block';
                }
            })
            .catch(function () {
                // ネットワーク断など、fetch自体が失敗した場合
                errorEl.textContent = '住所検索に失敗しました。手動で入力してください。';
                errorEl.style.display = 'block';
            })
            .finally(function () {
                searchBtn.disabled = false;
            });
    });
});
