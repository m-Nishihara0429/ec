package com.example.ec.config;

import com.example.ec.entity.Category;
import com.example.ec.entity.Faq;
import com.example.ec.entity.Product;
import com.example.ec.entity.Role;
import com.example.ec.entity.User;
import com.example.ec.repository.CategoryRepository;
import com.example.ec.repository.FaqRepository;
import com.example.ec.repository.ProductRepository;
import com.example.ec.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * アプリ起動時に初期データ（管理者・テストユーザー・カテゴリ・商品）を投入するクラス。
 * {@link CommandLineRunner} を実装しているため、起動完了直後に{@link #run}が自動実行される。
 * 投入されるデータは以下の通り（学習用ECサイトの動作確認用データ）：
 * ・管理者アカウント: admin@example.com / パスワード admin123（ROLE_ADMIN）
 * ・テスト用マスター管理者アカウント: master@example.com / パスワード master1234（ROLE_MASTER）
 * ・サイト運営者アカウント: owner@example.com / パスワードは app.master.password プロパティ（ROLE_MASTER。会員管理も可能な最上位ロール）
 * ・一般ユーザーアカウント: user@example.com / パスワード user1234（ROLE_USER）
 * ・カテゴリ: 書籍・家電・キッチン用品
 * ・商品: 各カテゴリにサンプル商品を2件ずつ、計6件
 */
@Component
public class DataInitializer implements CommandLineRunner {

    // ユーザー情報の検索・保存に使うリポジトリ
    private final UserRepository userRepository;
    // カテゴリ情報の検索・保存に使うリポジトリ
    private final CategoryRepository categoryRepository;
    // 商品情報の検索・保存に使うリポジトリ
    private final ProductRepository productRepository;
    // FAQ情報の検索・保存に使うリポジトリ
    private final FaqRepository faqRepository;
    // パスワードをハッシュ化するためのエンコーダー（平文のまま保存しないため）
    private final PasswordEncoder passwordEncoder;
    // マスター管理者アカウントのパスワード。実際のパスワードをソースコードに残さないよう、
    // 環境変数 APP_MASTER_PASSWORD（application.propertiesのapp.master.password）から読み込む。
    // 未設定時（ローカル開発など）は無害なプレースホルダー値にフォールバックする。
    private final String masterPassword;

    /**
     * コンストラクタ。Spring DIによって各リポジトリ・エンコーダーが自動的に注入される。
     * @param userRepository ユーザーリポジトリ
     * @param categoryRepository カテゴリリポジトリ
     * @param productRepository 商品リポジトリ
     * @param faqRepository FAQリポジトリ
     * @param passwordEncoder パスワードエンコーダー
     * @param masterPassword マスター管理者アカウントのパスワード（app.master.propertyから注入）
     */
    public DataInitializer(UserRepository userRepository, CategoryRepository categoryRepository,
                            ProductRepository productRepository, FaqRepository faqRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${app.master.password:ChangeMe_Master2026}") String masterPassword) {
        // 各フィールドに依存を保持する
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.faqRepository = faqRepository;
        this.passwordEncoder = passwordEncoder;
        this.masterPassword = masterPassword;
    }

    /**
     * アプリ起動完了直後にSpring Bootが自動的に呼び出すメソッド。
     * 初期データが未登録であれば投入し、既にあれば何もしない（冪等な処理）。
     * @param args コマンドライン引数（未使用）
     */
    @Override
    public void run(String... args) {
        // 管理者アカウントが未登録なら作成する（既に存在する場合は何もしない＝冪等）
        if (userRepository.findByEmail("admin@example.com").isEmpty()) {
            // 新規Userエンティティを作成
            User admin = new User();
            // 表示名を設定
            admin.setName("管理者");
            // ログインID代わりのメールアドレスを設定
            admin.setEmail("admin@example.com");
            // パスワード"admin123"をハッシュ化して設定（平文はDBに保存しない）
            admin.setPassword(passwordEncoder.encode("admin123"));
            // ロールを管理者(ROLE_ADMIN)に設定
            admin.setRole(Role.ROLE_ADMIN);
            // DBに保存する
            userRepository.save(admin);
        }

        // 動作確認用のマスター管理者アカウントが未登録なら作成する（admin/userと同様の固定パスワードのテストアカウント）
        if (userRepository.findByEmail("master@example.com").isEmpty()) {
            // 新規Userエンティティを作成
            User testMaster = new User();
            // 表示名を設定
            testMaster.setName("テストマスター");
            // ログインID代わりのメールアドレスを設定
            testMaster.setEmail("master@example.com");
            // パスワード"master1234"をハッシュ化して設定
            testMaster.setPassword(passwordEncoder.encode("master1234"));
            // ロールをマスター管理者(ROLE_MASTER)に設定
            testMaster.setRole(Role.ROLE_MASTER);
            // DBに保存する
            userRepository.save(testMaster);
        }

        // サイト運営者用のマスター管理者アカウント。未作成、またはパスワードが
        // 期待値と一致しない場合は作成・更新してログインできる状態を保つ。
        // マスター管理者（ROLE_MASTER）は通常の管理者（ROLE_ADMIN）の操作に加えて、
        // 会員のロール変更・アカウント有効/無効化ができる最上位ロール。
        // 既存の運営者アカウントを検索し、存在しなければ空のUserを新規作成する
        User owner = userRepository.findByEmail("owner@example.com").orElseGet(User::new);
        // IDが未採番（新規）、またはパスワードが期待値と一致しない場合に作成・更新処理を行う
        if (owner.getId() == null || !passwordEncoder.matches(masterPassword, owner.getPassword())) {
            // 表示名を設定
            owner.setName("masa");
            // ログインID代わりのメールアドレスを設定
            owner.setEmail("owner@example.com");
            // 期待するパスワードをハッシュ化して設定（既存パスワードとズレていれば上書き）
            owner.setPassword(passwordEncoder.encode(masterPassword));
            // ロールをマスター管理者(ROLE_MASTER)に設定
            owner.setRole(Role.ROLE_MASTER);
            // DBに保存（新規INSERTまたは既存レコードのUPDATE）する
            userRepository.save(owner);
        } else if (owner.getRole() != Role.ROLE_MASTER) {
            // ROLE_MASTER導入前に作成された既存DBでは、このアカウントがROLE_ADMINのまま残っている場合がある
            // （パスワードは一致しているため上のif文には入らない）。ロールだけをMASTERへ補正する、
            // 何度起動しても安全な（べき等な）補正処理。
            owner.setRole(Role.ROLE_MASTER);
            userRepository.save(owner);
        }

        // 動作確認用の一般ユーザーアカウントが未登録なら作成する
        if (userRepository.findByEmail("user@example.com").isEmpty()) {
            // 新規Userエンティティを作成
            User user = new User();
            // 表示名を設定
            user.setName("テストユーザー");
            // ログインID代わりのメールアドレスを設定
            user.setEmail("user@example.com");
            // パスワード"user1234"をハッシュ化して設定
            user.setPassword(passwordEncoder.encode("user1234"));
            // ロールを一般ユーザー(ROLE_USER)に設定
            user.setRole(Role.ROLE_USER);
            // DBに保存する
            userRepository.save(user);
        }

        // カテゴリが1件も無い（初回起動）場合のみ、サンプルカテゴリと商品を登録する
        if (categoryRepository.count() == 0) {
            // 「書籍」カテゴリを保存し、保存後の（IDが採番された）エンティティを受け取る
            Category books = categoryRepository.save(new Category("書籍"));
            // 「家電」カテゴリを保存する
            Category electronics = categoryRepository.save(new Category("家電"));
            // 「キッチン用品」カテゴリを保存する
            Category kitchen = categoryRepository.save(new Category("キッチン用品"));

            // 書籍カテゴリのサンプル商品1: Java入門書（税別価格2800円、在庫15点）
            saveProduct("Javaプログラミング入門", "初心者向けのJava学習書です。", 2800, 15, books);
            // 書籍カテゴリのサンプル商品2: Spring Boot解説書（価格3200円、在庫10点）
            saveProduct("Spring Boot実践ガイド", "Spring Bootでの開発手法を解説する一冊。", 3200, 10, books);
            // 家電カテゴリのサンプル商品1: ワイヤレスイヤホン（価格8900円、在庫25点、商品画像あり）
            saveProduct("ワイヤレスイヤホン", "ノイズキャンセリング機能付き。", 8900, 25, electronics, "/img/products/iyahon.png");
            // 家電カテゴリのサンプル商品2: スマートウォッチ（価格15800円、在庫12点、商品画像あり）
            saveProduct("スマートウォッチ", "健康管理機能を搭載したスマートウォッチ。", 15800, 12, electronics, "/img/products/smartwatch.png");
            // キッチン用品カテゴリのサンプル商品1: 電気ケトル（価格4500円、在庫20点、商品画像あり）
            saveProduct("電気ケトル", "1分で沸騰する高速電気ケトル。", 4500, 20, kitchen, "/img/products/denkiketoru.png");
            // キッチン用品カテゴリのサンプル商品2: ステンレスタンブラー（価格1800円、在庫40点、商品画像あり）
            saveProduct("ステンレスタンブラー", "保温・保冷に優れたタンブラー。", 1800, 40, kitchen, "/img/products/stainless-tumbler.png");
        }

        // 上記の初回投入より前に作られたDB（ecsite.db）には、まだ画像URLが設定されていない
        // レコードが残っている場合があるため、商品名で画像パスを引き当てて
        // 画像URLが未設定なら補完する（何度起動しても安全な、べき等な補正処理）
        Map<String, String> imagesByProductName = Map.of(
                "ステンレスタンブラー", "/img/products/stainless-tumbler.png",
                "ワイヤレスイヤホン", "/img/products/iyahon.png",
                "スマートウォッチ", "/img/products/smartwatch.png",
                "電気ケトル", "/img/products/denkiketoru.png"
        );
        productRepository.findAll().stream()
                // 画像URLが未設定（null または空文字）のものだけに絞り込む
                .filter(p -> p.getImageUrl() == null || p.getImageUrl().isBlank())
                // 商品名に対応する画像パスが定義されている商品だけに絞り込む
                .filter(p -> imagesByProductName.containsKey(p.getName()))
                // 該当する商品に画像URLを設定してDBに保存する
                .forEach(p -> {
                    p.setImageUrl(imagesByProductName.get(p.getName()));
                    productRepository.save(p);
                });

        // FAQが1件も無い（初回起動）場合のみ、サンプルのよくある質問を登録する
        if (faqRepository.count() == 0) {
            saveFaq("会員登録をしないと商品を購入できませんか？",
                    "商品の閲覧・検索は登録不要でご利用いただけますが、カートへの追加やご注文には会員登録（無料）が必要です。",
                    1);
            saveFaq("支払い方法を教えてください",
                    "ご注文確定時に配送先住所をご入力いただくことでご注文が確定します。本サイトは学習用のデモサイトのため、実際の決済処理は行っておりません。",
                    2);
            saveFaq("注文をキャンセルすることはできますか？",
                    "マイページの「注文履歴」から、発送準備前（ステータスが「処理中」）の注文はご自身でキャンセルいただけます。発送準備開始後のキャンセルをご希望の場合はお問い合わせフォームよりご連絡ください。",
                    3);
            saveFaq("パスワードを忘れてしまいました",
                    "ログイン画面の「パスワードをお忘れですか？」リンクから、メールアドレスを入力して再設定を行うことができます。",
                    4);
            saveFaq("商品にレビューを投稿できますか？",
                    "ログイン済みの会員であれば、商品詳細ページから星評価とコメントを投稿できます（1商品につきお1人1回まで）。",
                    5);
            saveFaq("在庫切れの商品は再入荷しますか？",
                    "再入荷の時期は商品によって異なります。気になる商品がございましたら、お問い合わせフォームよりお気軽にお問い合わせください。",
                    6);
        }
    }

    /**
     * サンプルFAQを1件組み立ててDBに保存するヘルパーメソッド。
     * @param question 質問文
     * @param answer 回答文
     * @param displayOrder 表示順（小さい値ほど先に表示）
     */
    private void saveFaq(String question, String answer, int displayOrder) {
        Faq faq = new Faq();
        faq.setQuestion(question);
        faq.setAnswer(answer);
        faq.setDisplayOrder(displayOrder);
        faqRepository.save(faq);
    }

    /**
     * サンプル商品を1件組み立ててDBに保存するヘルパーメソッド（画像URLなし）。
     * 内部的には画像URLに null を渡す {@link #saveProduct(String, String, int, int, Category, String)} の簡易版。
     * @param name 商品名
     * @param description 商品説明
     * @param price 価格
     * @param stock 在庫数
     * @param category 所属カテゴリ
     */
    private void saveProduct(String name, String description, int price, int stock, Category category) {
        // 画像URLなし（null）として、本体の実装に委譲する
        saveProduct(name, description, price, stock, category, null);
    }

    /**
     * サンプル商品を1件組み立ててDBに保存するヘルパーメソッド（画像URLあり）。
     * @param name 商品名
     * @param description 商品説明
     * @param price 価格
     * @param stock 在庫数
     * @param category 所属カテゴリ
     * @param imageUrl 商品画像のパス（static配下からの絶対パス。未使用の場合はnull）
     */
    private void saveProduct(String name, String description, int price, int stock, Category category, String imageUrl) {
        // 新規Productエンティティを作成
        Product product = new Product();
        // 商品名を設定
        product.setName(name);
        // 商品説明を設定
        product.setDescription(description);
        // 価格を設定
        product.setPrice(price);
        // 在庫数を設定
        product.setStock(stock);
        // 所属カテゴリを設定
        product.setCategory(category);
        // 画像URLを設定（nullの場合は「画像なし」としてテンプレート側でNo Image表示になる）
        product.setImageUrl(imageUrl);
        // DBに保存する
        productRepository.save(product);
    }
}
