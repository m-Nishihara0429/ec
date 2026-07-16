package com.example.ec.service;

import com.example.ec.dto.SalesPoint;
import com.example.ec.entity.*;
import com.example.ec.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注文（チェックアウト・照会・ステータス変更・キャンセル）を扱うサービスクラス。
 *
 * <p>このクラスの中でも特に重要なのは以下の2点。</p>
 * <ul>
 *   <li>在庫の整合性維持：checkout時にProductService.decreaseStockを通じて
 *       「在庫チェック＋減算」を1つのUPDATE文で原子的に行うことで、複数ユーザーが
 *       同時に同じ商品を注文しても在庫がマイナスにならないようにしている
 *       （実際の排他制御・原子的更新の実装はProductService側にある）。</li>
 *   <li>注文キャンセル時の在庫復元：cancelメソッドで、キャンセルされた注文の
 *       明細ごとに在庫を元に戻す（increaseStock）ことで、在庫数の整合性を保つ。</li>
 * </ul>
 * <p>また、一般ユーザーと管理者とでキャンセル可能な条件が異なる権限チェックも
 * このクラスの重要な責務（cancelByUser / cancelByAdmin を参照）。</p>
 */
@Service // Springのサービス層Beanとして登録する
public class OrderService {

    // 注文（Order）のデータアクセスを担当するリポジトリ
    private final OrderRepository orderRepository;
    // カート操作（カート内容取得・カートクリア）に使うサービス
    private final CartService cartService;
    // 商品の在庫増減（原子的更新）に使うサービス
    private final ProductService productService;
    // クーポンの検証・利用回数の原子的な加算に使うサービス
    private final CouponService couponService;

    // コンストラクタインジェクションで必要な依存を受け取る
    public OrderService(OrderRepository orderRepository, CartService cartService, ProductService productService,
                         CouponService couponService) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.productService = productService;
        this.couponService = couponService;
    }

    /**
     * カートの内容から注文（Order）を作成する（チェックアウト処理）。
     * 処理の流れ：
     * 1) カートが空でないことを確認する
     * 2) 注文ヘッダー（Order）を作り、ステータスをPENDING（未処理・発送準備前）にする
     * 3) カート明細ごとに、在庫を原子的に減算しつつ注文明細（OrderItem）を積み上げる
     * 4) 合計金額を計算して注文に設定し、注文を保存する
     * 5) 注文が確定したのでカートを空にする
     *
     * <p>在庫減算はProductService.decreaseStockが「在庫チェック＋減算」を1つのSQLで
     * 原子的に行うため、このメソッド自体で追加のロック処理をしなくても、
     * 同時に複数ユーザーが同じ商品を注文した場合の在庫マイナス超過を防げる。
     * また、@Transactionalによりこのメソッド内の一連のDB操作（在庫減算・注文保存・カートクリア）は
     * 1つのトランザクションとして扱われ、途中で例外（例：在庫不足）が発生した場合は
     * それまでに行った在庫減算やカート操作もすべてロールバックされ、データの不整合が残らない。</p>
     *
     * @param user          注文するユーザー
     * @param address       配送先住所
     * @param paymentMethod 支払い方法
     * @param couponCode    適用するクーポンコード（未入力ならnullまたは空文字）
     * @return 保存された注文（Order）エンティティ
     * @throws IllegalStateException カートが空の場合、いずれかの商品の在庫が不足している場合、
     *                                クーポンが無効・条件未達・利用回数上限に達している場合、
     *                                またはそのユーザーが同じクーポンを既に利用済み（1人1回まで）の場合
     */
    // カート内容から注文を作成する。各商品の在庫を減算し、注文明細・合計金額を積み上げたうえでカートを空にする
    @Transactional // 在庫減算・クーポン適用・注文保存・カートクリアを1つの原子的な処理としてまとめ、途中失敗時は全体をロールバックする
    public Order checkout(User user, String address, PaymentMethod paymentMethod, String couponCode) {
        // ユーザーのカート内容（商品と数量のリスト）を取得する
        List<CartItem> cartItems = cartService.findByUser(user);
        // カートが空であれば注文を作る意味が無いため例外を投げて処理を中断する
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("カートが空です");
        }

        // 新しい注文エンティティを生成する
        Order order = new Order();
        // 注文者を設定する
        order.setUser(user);
        // 配送先住所を設定する
        order.setAddress(address);
        // 支払い方法を設定する
        order.setPaymentMethod(paymentMethod);
        // 初期ステータスはPENDING（注文直後・未処理）とする
        order.setStatus(OrderStatus.PENDING);

        // 注文全体の合計金額（割引前）を積み上げるための変数（初期値0）
        int subtotal = 0;
        // カートの各明細（商品ごと）についてループ処理を行う
        for (CartItem cartItem : cartItems) {
            // カート明細から対象商品を取り出す
            Product product = cartItem.getProduct();
            // 在庫を「チェック＋減算」を原子的に行うメソッドで減らす。在庫不足なら例外がここで投げられる
            // （同時に他ユーザーが注文しても在庫がマイナスにならないよう、原子的なUPDATEで競合を防いでいる）
            productService.decreaseStock(product, cartItem.getQuantity());
            // 注文明細（OrderItem）を作成する。価格はチェックアウト時点の商品価格をそのままコピーして固定する
            // （後から商品価格が変わっても、既に成立した注文の金額が変わらないようにするため）
            OrderItem orderItem = new OrderItem(product, cartItem.getQuantity(), product.getPrice());
            // 作成した注文明細を注文（Order）に追加する
            order.addItem(orderItem);
            // この明細の小計（単価×数量）を合計金額に加算する
            subtotal += orderItem.subtotal();
        }

        // クーポンコードが入力されている場合のみ、検証・割引額計算・利用回数の加算を行う
        int discount = 0;
        if (couponCode != null && !couponCode.isBlank()) {
            // 有効期間・最低注文金額などを検証する（不正なら例外が投げられ、在庫減算含め全体がロールバックされる）
            Coupon coupon = couponService.validate(couponCode, subtotal);
            // 同一ユーザーが同じクーポンを既に利用済み（キャンセル済み注文を除く）でないかを確認する。
            // 1人1クーポン1回までとし、二重利用を防ぐ
            if (orderRepository.existsByUserAndCouponCodeAndStatusNot(user, coupon.getCode(), OrderStatus.CANCELLED)) {
                throw new IllegalStateException("このクーポンは既にご利用済みです");
            }
            discount = coupon.calculateDiscount(subtotal);
            // 利用回数を原子的に加算する。検証時点と確定時点の間に他の注文が上限を使い切っていた場合はここで例外になる
            couponService.recordUsage(coupon);
            order.setCouponCode(coupon.getCode());
        }
        order.setDiscountAmount(discount);
        // 割引後の合計金額を注文に設定する（マイナスにはならない。calculateDiscountでsubtotal以下に制限済み）
        order.setTotalPrice(subtotal - discount);

        // 注文をDBに保存する
        Order saved = orderRepository.save(order);
        // 注文が確定したので、対応するカートの中身を空にする
        cartService.clear(user);
        // 保存済みの注文を返す
        return saved;
    }

    /**
     * 指定したユーザーの注文一覧を新しい順で取得する。
     *
     * @param user 対象ユーザー
     * @return そのユーザーの注文一覧（作成日時の降順）
     */
    public List<Order> findByUser(User user) {
        // ユーザーに紐づく注文を新着順で取得する
        return orderRepository.findByUserOrderByCreatedAtDesc(user);
    }

    /**
     * 全ユーザーの注文一覧を新しい順で取得する（管理画面向け）。
     *
     * @return 全注文一覧（作成日時の降順）
     */
    public List<Order> findAll() {
        // 全注文を新着順で取得する
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 直近の注文を最大5件取得する（管理画面のダッシュボード表示向け）。
     *
     * @return 直近の注文一覧（最大5件、作成日時の降順）
     */
    public List<Order> findRecent() {
        // 作成日時の新しい順に先頭5件だけ取得する
        return orderRepository.findTop5ByOrderByCreatedAtDesc();
    }

    /**
     * 指定したステータスの注文件数を取得する（管理画面の集計表示向け）。
     *
     * @param status 集計対象のステータス
     * @return 該当ステータスの注文件数
     */
    public long countByStatus(OrderStatus status) {
        // ステータスごとの件数をカウントする
        return orderRepository.countByStatus(status);
    }

    /**
     * 総売上高を取得する。キャンセル済み（CANCELLED）の注文は売上として数えないため除外する。
     *
     * @return キャンセルされた注文を除いた合計金額
     */
    public long totalSales() {
        // CANCELLEDステータスの注文を除いた合計金額をDB側で集計する
        return orderRepository.sumTotalPriceExcludingStatus(OrderStatus.CANCELLED);
    }

    // 日別・週別グラフの棒のラベル書式（例: "07/13"。週別では週の開始日をこの書式で表示する）
    private static final DateTimeFormatter DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("MM/dd");
    // 月別グラフの棒のラベル書式（例: "2026/07"）
    private static final DateTimeFormatter MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    /**
     * ダッシュボードの売上推移グラフ（日別）用に、直近 {@code days} 日分の売上合計を日ごとに集計する。
     * キャンセル済みの注文は売上から除外する。売上が0円の日も0として結果に含めるため、
     * 返される一覧は必ず {@code days} 件（日付の昇順）になる。
     *
     * @param days 何日分を集計するか（当日を含む）
     * @return 直近days日分の日別売上一覧（日付の昇順）
     */
    public List<SalesPoint> dailySales(int days) {
        // 集計対象期間の開始日（today - (days-1)）の0時0分0秒を起点とする
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1L);

        // まず全日を0円で埋めた集計マップを用意する（挿入順=日付昇順を保つためLinkedHashMapを使用）
        Map<LocalDate, Long> totalsByDate = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            totalsByDate.put(startDate.plusDays(i), 0L);
        }

        // 期間内・キャンセル除外の(注文日時, 合計金額)を取得し、日付単位に丸め込んで加算する
        List<Object[]> rows = orderRepository.findCreatedAtAndTotalPriceSince(
                OrderStatus.CANCELLED, startDate.atStartOfDay());
        for (Object[] row : rows) {
            LocalDateTime createdAt = (LocalDateTime) row[0];
            Integer totalPrice = (Integer) row[1];
            totalsByDate.merge(createdAt.toLocalDate(), totalPrice.longValue(), Long::sum);
        }

        // マップを画面表示用のDTOリストに変換する（挿入順＝日付昇順のまま）
        List<SalesPoint> result = new ArrayList<>();
        for (Map.Entry<LocalDate, Long> entry : totalsByDate.entrySet()) {
            result.add(new SalesPoint(entry.getKey().format(DAY_LABEL_FORMAT), entry.getValue()));
        }
        return result;
    }

    /**
     * ダッシュボードの売上推移グラフ（週別）用に、直近 {@code weeks} 週分の売上合計を
     * 7日単位のバケットに区切って集計する。最も古いバケットの開始日を基準に、
     * 経過日数を7で割った商でどのバケットに属するかを求める（日曜始まり等の暦週ではなく、
     * 当日を含む7日間ずつの単純な区切りである点に注意）。
     *
     * @param weeks 何週分を集計するか（直近の週を含む）
     * @return 直近weeks週分の週別売上一覧（週の昇順）
     */
    public List<SalesPoint> weeklySales(int weeks) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(weeks * 7L - 1);

        // バケットごとの開始日と合計金額を保持する配列（インデックス0が最も古い週）
        LocalDate[] weekStarts = new LocalDate[weeks];
        long[] totals = new long[weeks];
        for (int i = 0; i < weeks; i++) {
            weekStarts[i] = startDate.plusDays(i * 7L);
        }

        List<Object[]> rows = orderRepository.findCreatedAtAndTotalPriceSince(
                OrderStatus.CANCELLED, startDate.atStartOfDay());
        for (Object[] row : rows) {
            LocalDateTime createdAt = (LocalDateTime) row[0];
            Integer totalPrice = (Integer) row[1];
            LocalDate date = createdAt.toLocalDate();
            int index = (int) (ChronoUnit.DAYS.between(startDate, date) / 7);
            if (index >= 0 && index < weeks) {
                totals[index] += totalPrice;
            }
        }

        List<SalesPoint> result = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            result.add(new SalesPoint(weekStarts[i].format(DAY_LABEL_FORMAT) + "週", totals[i]));
        }
        return result;
    }

    /**
     * ダッシュボードの売上推移グラフ（月別）用に、直近 {@code months} ヶ月分の売上合計を
     * 暦月（1日〜月末）単位で集計する。
     *
     * @param months 何ヶ月分を集計するか（当月を含む）
     * @return 直近months ヶ月分の月別売上一覧（月の昇順）
     */
    public List<SalesPoint> monthlySales(int months) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(months - 1L);
        LocalDate startDate = startMonth.atDay(1);

        long[] totals = new long[months];
        List<Object[]> rows = orderRepository.findCreatedAtAndTotalPriceSince(
                OrderStatus.CANCELLED, startDate.atStartOfDay());
        for (Object[] row : rows) {
            LocalDateTime createdAt = (LocalDateTime) row[0];
            Integer totalPrice = (Integer) row[1];
            YearMonth yearMonth = YearMonth.from(createdAt.toLocalDate());
            int index = (int) startMonth.until(yearMonth, ChronoUnit.MONTHS);
            if (index >= 0 && index < months) {
                totals[index] += totalPrice;
            }
        }

        List<SalesPoint> result = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            result.add(new SalesPoint(startMonth.plusMonths(i).format(MONTH_LABEL_FORMAT), totals[i]));
        }
        return result;
    }

    /**
     * ダッシュボードのカテゴリ別売上棒グラフ（日別と同じ直近days日分）用に、カテゴリごとの売上合計を集計する。
     * 売上推移グラフ（日別）と対象期間を揃えることで、同じ期間内でどのカテゴリが売れているかを確認できる。
     *
     * @param days 何日分を集計対象にするか（当日を含む）
     * @return カテゴリ別売上一覧（売上金額の降順）
     */
    public List<SalesPoint> categorySalesDaily(int days) {
        return categorySalesSince(LocalDate.now().minusDays(days - 1L));
    }

    /**
     * ダッシュボードのカテゴリ別売上棒グラフ（週別と同じ直近weeks週分）用に、カテゴリごとの売上合計を集計する。
     *
     * @param weeks 何週分を集計対象にするか（直近の週を含む）
     * @return カテゴリ別売上一覧（売上金額の降順）
     */
    public List<SalesPoint> categorySalesWeekly(int weeks) {
        return categorySalesSince(LocalDate.now().minusDays(weeks * 7L - 1));
    }

    /**
     * ダッシュボードのカテゴリ別売上棒グラフ（月別と同じ直近months ヶ月分）用に、カテゴリごとの売上合計を集計する。
     *
     * @param months 何ヶ月分を集計対象にするか（当月を含む）
     * @return カテゴリ別売上一覧（売上金額の降順）
     */
    public List<SalesPoint> categorySalesMonthly(int months) {
        return categorySalesSince(YearMonth.now().minusMonths(months - 1L).atDay(1));
    }

    /**
     * カテゴリ別売上集計の共通処理。指定日付以降・キャンセル済みを除いた注文明細を
     * カテゴリごとに合計し、売上金額の多い順のDTO一覧に変換する。
     *
     * @param since 集計対象期間の開始日（この日を含む）
     * @return カテゴリ別売上一覧（売上金額の降順）
     */
    private List<SalesPoint> categorySalesSince(LocalDate since) {
        List<Object[]> rows = orderRepository.sumSalesByCategorySince(OrderStatus.CANCELLED, since.atStartOfDay());
        List<SalesPoint> result = new ArrayList<>();
        for (Object[] row : rows) {
            String categoryName = (String) row[0];
            Long total = (Long) row[1];
            result.add(new SalesPoint(categoryName, total));
        }
        return result;
    }

    /**
     * 注文をIDで1件取得する。
     *
     * @param id 取得したい注文のID
     * @return 該当する注文
     * @throws IllegalArgumentException 指定したIDの注文が存在しない場合
     */
    public Order findById(Long id) {
        // IDで注文を検索し、存在しなければ例外を投げる
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("注文が見つかりません: " + id));
    }

    /**
     * 注文のステータスを変更する（管理者による発送処理・完了処理などを想定）。
     *
     * @param orderId 対象の注文ID
     * @param status  設定したい新しいステータス
     */
    @Transactional // 検索と更新をまとめて1トランザクションにする
    public void updateStatus(Long orderId, OrderStatus status) {
        // IDで注文を取得する（存在しなければfindById内で例外）
        Order order = findById(orderId);
        // ステータスを新しい値に更新する
        order.setStatus(status);
        // 変更を保存する
        orderRepository.save(order);
    }

    /**
     * 一般ユーザー自身による注文キャンセル。
     * 権限チェックとして、以下の2条件を両方満たす場合のみキャンセルを許可する。
     * 1) 対象注文の注文者IDが、リクエストしてきたユーザー自身のIDと一致すること（他人の注文は操作不可）
     * 2) 注文のステータスがPENDING（発送準備前）であること（発送準備が始まった注文はユーザー側からは
     *    キャンセルできない仕様）
     * なお、他人の注文IDを指定された場合は「権限が無い」ではなく「見つからない」というエラーメッセージに
     * している。これは、注文の存在有無そのものを他ユーザーに悟られないようにするための配慮
     * （注文IDの存在を手がかりにした情報漏えいを防ぐ）。
     *
     * @param orderId キャンセル対象の注文ID
     * @param user    キャンセルを要求しているユーザー（ログイン中の本人）
     * @throws IllegalArgumentException 注文が存在しない、または他人の注文である場合
     * @throws IllegalStateException    注文のステータスがPENDINGでない（既に発送準備中など）場合
     */
    // 一般ユーザーは自分自身の注文かつステータスがPENDING（発送準備前）の場合のみキャンセルできる
    // 他人の注文の場合は存在自体を伏せるため「見つからない」エラーとする
    @Transactional // 検索・権限チェック・在庫復元・ステータス更新を1つの原子的な処理としてまとめる
    public void cancelByUser(Long orderId, User user) {
        // IDで注文を取得する
        Order order = findById(orderId);
        // 注文の所有者IDと、操作しようとしているユーザーのIDが一致するかを確認する
        if (!order.getUser().getId().equals(user.getId())) {
            // 一致しない（他人の注文である）場合は、注文の存在自体を伏せるため「見つからない」として扱う
            throw new IllegalArgumentException("注文が見つかりません: " + orderId);
        }
        // 注文のステータスがPENDING（発送準備前）でなければユーザーからのキャンセルは許可しない
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("発送準備中のため、この注文はキャンセルできません");
        }
        // 権限チェック・状態チェックを通過したので、実際のキャンセル処理（在庫復元＋ステータス変更）を行う
        cancel(order);
    }

    /**
     * 管理者による注文キャンセル。
     * 管理者は一般ユーザーと異なり、COMPLETED（完了済み）・CANCELLED（キャンセル済み）以外の
     * 注文であれば、ステータスを問わず（PENDING以外の発送準備中や発送済みでも）キャンセルできる。
     * これは、完了済みの注文（既に商品が届いている等）や既にキャンセル済みの注文を
     * 二重にキャンセルすることを防ぐための状態チェック。
     *
     * @param orderId キャンセル対象の注文ID
     * @throws IllegalStateException 注文が既にCOMPLETEDまたはCANCELLEDの場合
     */
    // 管理者はCOMPLETED（完了）・CANCELLED（キャンセル済み）以外の注文であれば、ステータスを問わずキャンセルできる
    @Transactional // 検索・状態チェック・在庫復元・ステータス更新を1つの原子的な処理としてまとめる
    public void cancelByAdmin(Long orderId) {
        // IDで注文を取得する
        Order order = findById(orderId);
        // 既に完了済み、または既にキャンセル済みの注文はこれ以上キャンセルできないようにする
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("この注文はキャンセルできません");
        }
        // 状態チェックを通過したので、実際のキャンセル処理（在庫復元＋ステータス変更）を行う
        cancel(order);
    }

    /**
     * 注文キャンセルの共通処理（在庫復元＋ステータス更新）。
     * cancelByUser / cancelByAdmin の両方から呼び出される内部専用メソッド。
     * 注文明細ごとにProductService.increaseStockを呼び出して在庫を元の数量まで戻し、
     * すべての明細を処理し終えた後に注文全体のステータスをCANCELLEDに変更して保存する。
     * increaseStockも原子的なUPDATE（increaseStockAtomic）を使っているため、
     * 他の注文処理と同時に実行されても在庫数の整合性が崩れない。
     *
     * @param order キャンセルする注文（呼び出し元で権限・状態チェック済みであることが前提）
     */
    // キャンセル時は注文明細ごとに在庫を戻し、ステータスをCANCELLEDに更新する共通処理
    private void cancel(Order order) {
        // 注文に含まれる明細（商品・数量）を1件ずつ処理する
        for (OrderItem item : order.getItems()) {
            // 各明細の数量分だけ、対象商品の在庫を原子的に増やして元に戻す
            productService.increaseStock(item.getProduct(), item.getQuantity());
        }
        // すべての明細の在庫復元が終わったら、注文全体のステータスをキャンセル済みに変更する
        order.setStatus(OrderStatus.CANCELLED);
        // 変更後の注文を保存する
        orderRepository.save(order);
    }
}
