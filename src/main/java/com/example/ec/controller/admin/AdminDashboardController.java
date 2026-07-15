package com.example.ec.controller.admin;

import com.example.ec.dto.SalesPoint;
import com.example.ec.entity.OrderStatus;
import com.example.ec.service.CategoryService;
import com.example.ec.service.OrderService;
import com.example.ec.service.ProductService;
import com.example.ec.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 管理者専用：売上・注文件数・在庫僅少商品などをまとめたダッシュボードを表示するコントローラー。
 * クラスに {@code @RequestMapping("/admin")} が付与されているため、
 * このコントローラーのURLは管理画面のトップ「/admin」となる。
 */
@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    // 在庫が「僅少」とみなす閾値（この個数以下の商品を一覧に表示する）
    private static final int LOW_STOCK_THRESHOLD = 5;
    // 売上推移グラフ（日別）に表示する日数（当日を含む直近14日間）
    private static final int DAILY_SALES_DAYS = 14;
    // 売上推移グラフ（週別）に表示する週数（直近12週間）
    private static final int WEEKLY_SALES_WEEKS = 12;
    // 売上推移グラフ（月別）に表示する月数（当月を含む直近12ヶ月）
    private static final int MONTHLY_SALES_MONTHS = 12;

    // 注文の集計（売上・件数など）を行うサービス
    private final OrderService orderService;
    // 商品の集計（件数・在庫僅少商品の取得）を行うサービス
    private final ProductService productService;
    // ユーザー数の集計を行うサービス
    private final UserService userService;
    // カテゴリ一覧の取得（件数集計用）を行うサービス
    private final CategoryService categoryService;

    // コンストラクタインジェクションで各サービスを受け取る
    public AdminDashboardController(OrderService orderService, ProductService productService,
                                     UserService userService, CategoryService categoryService) {
        this.orderService = orderService;
        this.productService = productService;
        this.userService = userService;
        this.categoryService = categoryService;
    }

    /**
     * GET /admin
     * 管理画面のダッシュボード（サマリー画面）を表示する。
     * 売上合計、ステータス別注文件数、直近の注文、在庫僅少商品、
     * 商品数・ユーザー数・カテゴリ数などをまとめて画面に渡す。
     *
     * @param period 売上推移グラフの集計単位。"daily"（日別）・"weekly"（週別）・"monthly"（月別）の
     *               いずれか。未指定または想定外の値の場合は"daily"として扱う
     * @param model  画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/dashboard"
     */
    @GetMapping
    public String dashboard(@RequestParam(name = "period", defaultValue = "daily") String period, Model model) {
        // 全注文の売上合計金額を画面に渡す
        model.addAttribute("totalSales", orderService.totalSales());
        // 「対応待ち（PENDING）」ステータスの注文件数を画面に渡す
        model.addAttribute("pendingCount", orderService.countByStatus(OrderStatus.PENDING));
        // 「発送済み（SHIPPED）」ステータスの注文件数を画面に渡す
        model.addAttribute("shippedCount", orderService.countByStatus(OrderStatus.SHIPPED));
        // 「完了（COMPLETED）」ステータスの注文件数を画面に渡す
        model.addAttribute("completedCount", orderService.countByStatus(OrderStatus.COMPLETED));
        // 「キャンセル（CANCELLED）」ステータスの注文件数を画面に渡す
        model.addAttribute("cancelledCount", orderService.countByStatus(OrderStatus.CANCELLED));
        // 直近の注文一覧（新着順など）を画面に渡す
        model.addAttribute("recentOrders", orderService.findRecent());
        // 在庫が閾値以下の商品一覧を画面に渡す（在庫補充のアラート表示に使う）
        model.addAttribute("lowStockProducts", productService.findLowStock(LOW_STOCK_THRESHOLD));
        // 登録されている商品の総数を画面に渡す
        model.addAttribute("productCount", productService.count());
        // 登録されているユーザーの総数を画面に渡す
        model.addAttribute("userCount", userService.count());
        // 登録されているカテゴリの総数（一覧を取得してそのサイズを使う）を画面に渡す
        model.addAttribute("categoryCount", categoryService.findAll().size());

        // 売上推移グラフ（日別／週別／月別を切り替え可能）を画面に渡す。
        // 想定外のperiod値が渡された場合はdailyとして扱う
        List<SalesPoint> salesPoints;
        switch (period) {
            case "weekly":
                salesPoints = orderService.weeklySales(WEEKLY_SALES_WEEKS);
                break;
            case "monthly":
                salesPoints = orderService.monthlySales(MONTHLY_SALES_MONTHS);
                break;
            default:
                period = "daily";
                salesPoints = orderService.dailySales(DAILY_SALES_DAYS);
        }
        // 画面側でタブの選択状態を出し分けるために、実際に採用した集計単位を渡す
        model.addAttribute("salesPeriod", period);
        model.addAttribute("salesPoints", salesPoints);
        // 棒グラフの高さをパーセント換算するための基準値（期間内の最大売上の金額）
        model.addAttribute("maxSalesPoint", salesPoints.stream()
                .mapToLong(SalesPoint::getTotal).max().orElse(0));

        // カテゴリ別売上（売上金額の降順）を画面に渡す。売上推移グラフと同じ期間（period）で絞り込む
        List<SalesPoint> categorySales;
        switch (period) {
            case "weekly":
                categorySales = orderService.categorySalesWeekly(WEEKLY_SALES_WEEKS);
                break;
            case "monthly":
                categorySales = orderService.categorySalesMonthly(MONTHLY_SALES_MONTHS);
                break;
            default:
                categorySales = orderService.categorySalesDaily(DAILY_SALES_DAYS);
        }
        model.addAttribute("categorySales", categorySales);
        // 棒グラフの高さをパーセント換算するための基準値（最も売れているカテゴリの金額）
        model.addAttribute("maxCategorySales", categorySales.stream()
                .mapToLong(SalesPoint::getTotal).max().orElse(0));

        // admin/dashboard.html（Thymeleafテンプレート）を表示する
        return "admin/dashboard";
    }
}
