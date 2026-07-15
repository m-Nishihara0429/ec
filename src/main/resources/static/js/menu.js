// ページのDOM構築が完了した後に、ハンバーガーメニュー（スマホなどで使う開閉式のナビゲーションドロワー）
// の開閉制御を初期化する処理。
document.addEventListener('DOMContentLoaded', function () {
    // ヘッダーにある「メニューを開く」ハンバーガーボタン要素を取得する。
    var openBtn = document.getElementById('menuOpenBtn');
    // ドロワー内にある「メニューを閉じる」×ボタン要素を取得する。
    var closeBtn = document.getElementById('menuCloseBtn');
    // 実際にスライドして表示される、ナビゲーションメニュー本体（ドロワー）の要素を取得する。
    var drawer = document.getElementById('navDrawer');
    // ドロワーが開いているときに背景を暗くする半透明のオーバーレイ要素を取得する。
    var overlay = document.getElementById('navDrawerOverlay');

    // ドロワー本体またはオーバーレイがページ内に存在しない場合は、
    // このページではハンバーガーメニュー機能を使わないと判断し、以降の処理を行わずに終了する。
    if (!drawer || !overlay) {
        return;
    }

    // ドロワーメニューを「開く」状態にするための関数。
    function openDrawer() {
        // ドロワーにopenクラスを付与し、CSS側のtransform（画面外→画面内へのスライドイン）を発動させる。
        drawer.classList.add('open');
        // オーバーレイにもopenクラスを付与し、背景を暗く表示（opacity/visibilityの切り替え）させる。
        overlay.classList.add('open');
        // スクリーンリーダー等の支援技術に対し、ドロワーが「非表示ではない（表示中）」ことを伝える。
        drawer.setAttribute('aria-hidden', 'false');
        // 開くボタンが存在する場合、aria-expandedを true にしてボタンが展開状態であることを示す。
        if (openBtn) openBtn.setAttribute('aria-expanded', 'true');
        // ドロワー表示中は背後のページが動かないよう、bodyにno-scrollクラスを付けてスクロールを禁止する。
        document.body.classList.add('no-scroll');
    }

    // ドロワーメニューを「閉じる」状態に戻すための関数。
    function closeDrawer() {
        // ドロワーからopenクラスを外し、CSS側のtransformで画面外へスライドアウトさせる。
        drawer.classList.remove('open');
        // オーバーレイからもopenクラスを外し、背景の暗さ（半透明の膜）を非表示にする。
        overlay.classList.remove('open');
        // 支援技術に対し、ドロワーが「非表示状態」であることを伝える。
        drawer.setAttribute('aria-hidden', 'true');
        // 開くボタンが存在する場合、aria-expandedを false に戻してボタンが閉じた状態であることを示す。
        if (openBtn) openBtn.setAttribute('aria-expanded', 'false');
        // ドロワーを閉じたら背後のページのスクロールを再度許可するため、no-scrollクラスを外す。
        document.body.classList.remove('no-scroll');
    }

    // 開くボタンが存在する場合、クリック時にopenDrawer関数を呼び出すイベントリスナーを登録する。
    if (openBtn) openBtn.addEventListener('click', openDrawer);
    // 閉じるボタンが存在する場合、クリック時にcloseDrawer関数を呼び出すイベントリスナーを登録する。
    if (closeBtn) closeBtn.addEventListener('click', closeDrawer);
    // オーバーレイ（背景の暗い部分）をクリックした場合も、メニュー外をタップしたとみなして閉じる。
    overlay.addEventListener('click', closeDrawer);
    // キーボードでキーが押されたときに発火するイベントを、ページ全体（document）に対して監視する。
    document.addEventListener('keydown', function (e) {
        // 押されたキーがEscapeキーだった場合は、ドロワーを閉じる（キーボード操作でも閉じられるようにする）。
        if (e.key === 'Escape') closeDrawer();
    });
});
