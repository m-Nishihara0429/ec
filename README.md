# Simple EC

Java（Spring Boot）で作った学習用のシンプルなECサイトです。会員登録・商品検索・カート・注文・レビュー・管理画面などECサイトの一通りの機能を実装しています。

詳細な設計（DB設計・画面遷移・セキュリティ設計など）は [docs/設計書.md](docs/設計書.md) を参照してください。このREADMEはセットアップと動かし方が中心です。

## 技術スタック

| 分類 | 採用技術 |
|---|---|
| 言語 | Java 17（ソース/バイトコードのターゲットは17。ビルド時のJDKは下記「必須環境」を参照） |
| フレームワーク | Spring Boot 3.3.x（Web / Data JPA / Security / Validation / Thymeleaf） |
| ビルドツール | Maven（Maven Wrapper同梱、`mvnw` / `mvnw.cmd`） |
| テンプレートエンジン | Thymeleaf（サーバーサイドレンダリング） |
| DB | SQLite（`ecsite.db`、ファイルベース） |
| 認証 | Spring Security（フォームログイン、BCrypt、Remember-Me） |
| 監視 | Spring Boot Actuator（health / info / metrics） |

## 必須環境

- **JDK 23**（重要）
  このプロジェクトは `pom.xml` 上は Java 17 をターゲットにしていますが、**ビルド時に使うJDK自体はJDK 23を使ってください**。JDK 24ではLombokとの組み合わせで以下のようなコンパイルエラーが発生することを確認しています。

  ```
  [ERROR] Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN
  ```

  JDK 23がインストールされていない場合は別途インストールしてください。

## セットアップ・起動方法

`JAVA_HOME` をJDK 23に向けてから、Maven Wrapperでビルド・起動します。

### Windows (PowerShell)

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"
.\mvnw.cmd spring-boot:run
```

### macOS / Linux

```bash
export JAVA_HOME=/path/to/jdk-23
./mvnw spring-boot:run
```

起動後、ブラウザで [http://localhost:8080](http://localhost:8080) を開いてください（ポートは `application.properties` の `server.port` で変更可能）。

> **`mvnw spring-boot:run` が `ClassNotFoundException: com.example.ec.EcApplication` で起動しない場合**
> プロジェクトのパスに日本語などの非ASCII文字が含まれていると、Windows環境では `spring-boot:run` が
> クラスパスを渡すために使う一時ファイル（`@argfile`）の解決に失敗することがあります。
> その場合は代わりに [run.ps1](run.ps1) を使ってください（`.\run.ps1`）。コンパイルと依存関係の
> クラスパス解決を行った上で `java -cp ...` で直接起動するため、この問題を回避できます。

初回起動時に [DataInitializer](src/main/java/com/example/ec/config/DataInitializer.java) がテストアカウントとサンプル商品（書籍・家電・キッチン用品カテゴリ、計6商品）を自動投入します。

## テストアカウント

| 用途 | メールアドレス | パスワード | 権限 |
|---|---|---|---|
| 管理者 | admin@example.com | admin123 | ADMIN |
| マスター管理者 | master@example.com | master1234 | MASTER |
| 一般ユーザー | user@example.com | user1234 | USER |

ログイン画面にもこのヒントが表示されます。

## テストの実行

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"
.\mvnw.cmd test
```

`OrderServiceTest` / `ProductServiceTest` などサービス層のテストで注文・在庫まわりのビジネスロジックを、
`MypageControllerTest` / `AuthControllerTest` などControllerテストでパスワード変更・会員登録・パスワード再設定の
正常系/異常系（バリデーションエラー、確認用パスワード不一致、CSRF拒否など）を、`LoginRateLimiterTest` で
ログインのレート制限ロジックをそれぞれカバーしています。

## 主な機能

**一般ユーザー向け**
- 会員登録・ログイン（Remember-Me対応、ログイン状態を1週間保持）
- パスワード再設定（メール送信は行わず、再設定リンクを画面に直接表示する簡易実装）
- マイページでの氏名変更・パスワード変更
- 商品検索（キーワード検索は表記ゆれ・同義語に対応、価格帯はデュアルスライダーで絞り込み）
- カテゴリ絞り込み・並び替え（新着順／価格順／評価順）
- カート → チェックアウト → 注文確定（郵便番号を入力すると外部API（zipcloud）から住所を自動取得）
- 注文履歴の閲覧、注文のキャンセル（発送前のみ・在庫が自動的に戻る）
- 商品レビュー（star評価＋コメント、1商品につき1人1件）
- ハンバーガーメニュー（カテゴリ・アカウント・注文履歴への導線）

**管理者向け**
- ダッシュボード（売上推移グラフ（日別／週別／月別）・カテゴリ別売上・注文ステータス内訳・直近の注文・在庫僅少商品）
- 商品管理（CRUD、CSVファイルからの一括登録に対応）
- カテゴリ管理（CRUD）
- 注文管理（ステータス変更、キャンセル）
- 会員管理（マスター管理者専用。会員のロール変更、アカウントの有効/無効化）

**権限ロール**
- `ROLE_USER`: 一般ユーザー
- `ROLE_ADMIN`: 管理者（商品・カテゴリ・注文・FAQ・問い合わせの管理）
- `ROLE_MASTER`: マスター管理者。ROLE_ADMINの操作に加えて会員管理（ロール変更・アカウント有効/無効化）が可能な最上位ロール

## セキュリティ・運用機能

**監視（Actuator）**
- `/actuator/health` は未認証で公開（Renderなどのヘルスチェックから利用するため）。詳細情報
  （DB接続状況等）はADMIN権限でログインしている場合のみ表示される（`show-details=when-authorized`）。
- `/actuator/info` ・ `/actuator/metrics`（JVM/HTTPメトリクス）はADMIN権限のユーザーのみアクセス可能。
- `env` ・ `beans` など機密情報を含みうるエンドポイントはそもそも有効化していない。

**ロギング**
- ログイン成否（IPアドレス付き）、パスワード変更・会員登録・注文確定/キャンセル・会員ロール変更などの
  重要操作を`INFO`/`WARN`レベルで記録する（`logging.level.com.example.ec=INFO`）。
- ログイン監査はSpring Securityの認証イベント（`AuthenticationSuccessEvent`等）を購読する
  `SecurityAuditListener`が担い、remember-meによる自動再認証（フォーム入力を伴わない）は
  ログイン試行として扱わないようにしている。

**ログインのレート制限（ブルートフォース対策）**
- 同一IPアドレスから15分間に5回ログインに失敗すると、以降のログイン試行を429（Too Many Requests）で
  一時的に拒否する（`LoginRateLimiter` / `LoginRateLimitFilter`）。
- インメモリ実装のため、複数インスタンスに水平スケールする場合は各インスタンスが個別にカウントする
  （Redis等の共有ストアへの置き換えが必要）。また、IPアドレス単位の制限のため、NAT配下の
  オフィスネットワークなど複数ユーザーが同一IPを共有する環境では、1人の失敗が同じIPを使う
  他のユーザーのログインにも影響しうる点に注意（学習用途の単一インスタンス運用を前提とした簡易実装）。
- 本番（Render等のリバースプロキシ配下）でクライアントの実IPを正しく取得できるよう、
  `server.forward-headers-strategy=framework`を設定し、`X-Forwarded-For`ヘッダーから実IPを復元している。

## 既知の制約

- パスワード再設定はメール送信を行わず、画面上にリンクを表示する簡易実装です（SMTP未設定のため）。実運用する場合は [PasswordResetService](src/main/java/com/example/ec/service/PasswordResetService.java) から `spring-boot-starter-mail` 経由でメール送信するよう差し替えてください。
- DBはSQLiteのファイル（`ecsite.db`）で、`spring.jpa.hibernate.ddl-auto=update` によりスキーマは自動更新されますが、CHECK制約など一部の変更は自動反映されないため、列挙型（enum）に選択肢を追加する場合は手動でのスキーマ移行が必要になることがあります。
- 決済ゲートウェイ連携は未実装です（対象外）。

## プロジェクト構成

```
com.example.ec
 ├─ config/          SecurityConfig, DataInitializer, GlobalModelAdvice など
 ├─ entity/          User, Product, Order, OrderItem, Review など
 ├─ repository/      Spring Data JPA リポジトリ
 ├─ specification/   商品検索条件（Specification）
 ├─ util/            TextNormalizer（表記ゆれ吸収）
 ├─ constant/        SearchSynonyms（同義語辞書）
 ├─ service/         ビジネスロジック
 ├─ controller/       一般ユーザー向けコントローラー
 ├─ controller/admin/ 管理者向けコントローラー
 └─ dto/             フォーム入力用DTO
```

より詳しいDB設計・画面遷移・セキュリティ設計は [docs/設計書.md](docs/設計書.md) を参照してください。
