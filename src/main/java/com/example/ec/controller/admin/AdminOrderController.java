package com.example.ec.controller.admin;

import com.example.ec.entity.Order;
import com.example.ec.entity.OrderStatus;
import com.example.ec.service.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;

/**
 * 管理者専用：注文の一覧表示・詳細確認・ステータス変更・キャンセルを担当するコントローラー。
 * クラスに {@code @RequestMapping("/admin/orders")} が付与されているため、
 * 各メソッドのURLは「/admin/orders」を起点とした相対パスになる。
 * 管理者は全ユーザーの注文を横断的に扱えるため、一般ユーザー向けOrderControllerと異なり
 * 所有者チェックは行わない。
 */
@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {

    // 注文の取得・全件取得・ステータス更新・キャンセルを行うサービス
    private final OrderService orderService;

    // コンストラクタインジェクションでサービスを受け取る
    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * GET /admin/orders
     * 全ユーザー分の注文一覧画面を表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/orders"
     */
    @GetMapping
    public String list(Model model) {
        // 全ユーザーの注文一覧を取得して画面に渡す
        model.addAttribute("orders", orderService.findAll());
        // admin/orders.html（Thymeleafテンプレート）を表示する
        return "admin/orders";
    }

    /**
     * GET /admin/orders/{id}
     * 注文詳細画面を表示する。あわせてステータス変更プルダウンの選択肢も渡す。
     *
     * @param id    表示する注文のID（URLパス変数）
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/order_detail"
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        // パス変数で指定された注文IDから注文エンティティを取得する
        Order order = orderService.findById(id);
        // 注文情報を画面に渡す
        model.addAttribute("order", order);
        // ステータス変更プルダウンにはCANCELLEDを含めない（キャンセルは専用のキャンセル処理で行う）
        model.addAttribute("statuses", Arrays.stream(OrderStatus.values())
                .filter(s -> s != OrderStatus.CANCELLED)
                .toList());
        // admin/order_detail.html（Thymeleafテンプレート）を表示する
        return "admin/order_detail";
    }

    /**
     * POST /admin/orders/{id}/status
     * 注文のステータスを更新する（例: PENDING → SHIPPED など）。
     * 完了済み・キャンセル済みの注文への変更や、CANCELLEDへの直接変更はサービス層で拒否され、
     * フラッシュメッセージとしてエラー内容を注文詳細画面に伝える。
     *
     * @param id                 ステータスを変更する注文のID（URLパス変数）
     * @param status             変更後のステータス（フォームのリクエストパラメータ）
     * @param redirectAttributes 変更不可時のエラーメッセージをフラッシュ属性として伝えるための機構
     * @return 注文詳細画面へリダイレクト
     */
    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam OrderStatus status,
                                RedirectAttributes redirectAttributes) {
        try {
            // 指定された注文のステータスを更新する（終端状態からの変更・CANCELLEDへの直接変更はサービス層で拒否される）
            orderService.updateStatus(id, status);
        } catch (IllegalStateException e) {
            // 変更できない状態だった場合、エラーメッセージをフラッシュ属性として設定する
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        // 更新後は同じ注文の詳細画面へリダイレクトする
        return "redirect:/admin/orders/" + id;
    }

    /**
     * POST /admin/orders/{id}/cancel
     * 管理者権限で注文をキャンセルする。
     * 既に発送済みなどキャンセルできない状態の場合はサービス層で例外が発生し、
     * フラッシュメッセージとしてエラー内容を注文詳細画面に伝える。
     *
     * @param id                 キャンセル対象の注文ID（URLパス変数）
     * @param redirectAttributes リダイレクト先の画面に一度だけ表示するフラッシュメッセージを設定するための機構
     * @return 注文詳細画面へリダイレクト
     */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // キャンセル可否の判定（既に発送済み等）はサービス層で行われる
            orderService.cancelByAdmin(id);
        } catch (IllegalStateException e) {
            // キャンセルできない状態だった場合、エラーメッセージをフラッシュ属性として
            // リダイレクト後の画面に一度だけ表示できるようにセットする
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        // 成功・失敗いずれの場合も注文詳細画面へリダイレクトする
        return "redirect:/admin/orders/" + id;
    }
}
