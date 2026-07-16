package com.example.ec.controller;

import com.example.ec.config.SecurityUserDetails;
import com.example.ec.dto.CheckoutForm;
import com.example.ec.entity.CartItem;
import com.example.ec.entity.Order;
import com.example.ec.entity.PaymentMethod;
import com.example.ec.entity.User;
import com.example.ec.service.CartService;
import com.example.ec.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 注文（チェックアウト・注文完了・注文履歴・キャンセル）とマイページトップを担当するコントローラー。
 * 注文情報は個人情報を含むため、注文詳細・完了画面ではログイン中ユーザーが
 * その注文の所有者本人であるかを都度チェックし、他人の注文を閲覧できないようにしている。
 */
@Controller
public class OrderController {

    // 注文の作成・取得・ステータス更新・キャンセルを行うサービス
    private final OrderService orderService;
    // カート内容の取得・合計金額計算を行うサービス
    private final CartService cartService;

    // コンストラクタインジェクションで各サービスを受け取る
    public OrderController(OrderService orderService, CartService cartService) {
        this.orderService = orderService;
        this.cartService = cartService;
    }

    /**
     * GET /checkout
     * チェックアウト（注文確認）画面を表示する。カートが空の場合はカート画面に戻す。
     *
     * @param principal ログイン中ユーザーの情報
     * @param model     画面に渡すデータの入れ物
     * @return カートが空なら "redirect:/cart"、それ以外は "order/checkout"
     */
    @GetMapping("/checkout")
    public String checkoutForm(@AuthenticationPrincipal SecurityUserDetails principal, Model model) {
        // ログイン中のユーザーエンティティを取り出す
        User user = principal.getUser();
        // ユーザーのカート内商品一覧を取得する
        List<CartItem> items = cartService.findByUser(user);
        // カートが空のままチェックアウト画面に進めないようカート一覧へ戻す
        if (items.isEmpty()) {
            return "redirect:/cart";
        }
        // カート内商品一覧を画面に渡す
        model.addAttribute("items", items);
        // カート合計金額を画面に渡す
        model.addAttribute("total", cartService.totalPrice(user));
        // 配送先住所などを入力するチェックアウトフォームの空DTOを画面に渡す
        model.addAttribute("checkoutForm", new CheckoutForm());
        // 支払い方法の選択肢一覧を画面に渡す
        model.addAttribute("paymentMethods", PaymentMethod.values());
        // order/checkout.html（Thymeleafテンプレート）を表示する
        return "order/checkout";
    }

    /**
     * POST /checkout
     * チェックアウトフォームの内容をもとに注文を確定する。
     * バリデーションエラーや在庫不足などの業務エラー時はチェックアウト画面を再表示する。
     *
     * @param principal     ログイン中ユーザーの情報
     * @param form          送信されたチェックアウト内容（配送先住所など）。{@code @Valid}で入力チェックを行う
     * @param bindingResult バリデーション結果
     * @param model         画面に渡すデータの入れ物
     * @return エラー時は "order/checkout"、成功時は注文完了画面へのリダイレクト
     */
    @PostMapping("/checkout")
    public String checkout(@AuthenticationPrincipal SecurityUserDetails principal,
                            @Valid @ModelAttribute("checkoutForm") CheckoutForm form,
                            BindingResult bindingResult,
                            Model model) {
        // ログイン中のユーザーエンティティを取り出す
        User user = principal.getUser();
        // 入力項目自体のバリデーションエラーがあればカート情報を詰め直して画面を再表示する
        if (bindingResult.hasErrors()) {
            model.addAttribute("items", cartService.findByUser(user));
            model.addAttribute("total", cartService.totalPrice(user));
            model.addAttribute("paymentMethods", PaymentMethod.values());
            return "order/checkout";
        }
        Order order;
        try {
            // 在庫切れ・クーポン無効等、注文確定時にサービス層で検知される状態異常はエラーメッセージ付きで
            // チェックアウト画面に戻す
            order = orderService.checkout(user, form.getAddress(), form.getPaymentMethod(), form.getCouponCode());
        } catch (IllegalStateException e) {
            // 業務エラー発生時はカート情報とエラーメッセージを詰め直して画面を再表示する
            model.addAttribute("items", cartService.findByUser(user));
            model.addAttribute("total", cartService.totalPrice(user));
            model.addAttribute("paymentMethods", PaymentMethod.values());
            model.addAttribute("errorMessage", e.getMessage());
            return "order/checkout";
        } catch (DataIntegrityViolationException e) {
            // 同一ユーザーが同じクーポンで複数のチェックアウトリクエストをほぼ同時に送信した場合、
            // OrderService側のexistsByUserAndCouponCodeAndStatusNotチェックをすり抜けて両方とも検証を通過することがあるが、
            // Order側のユニーク制約(uk_orders_user_coupon)によりDB保存時にここで検知される。
            // DataIntegrityViolationExceptionはDataAccessExceptionのサブクラスのため、下のcatchより先に置く必要がある
            model.addAttribute("items", cartService.findByUser(user));
            model.addAttribute("total", cartService.totalPrice(user));
            model.addAttribute("paymentMethods", PaymentMethod.values());
            model.addAttribute("errorMessage", "このクーポンは既にご利用済みです");
            return "order/checkout";
        } catch (DataAccessException e) {
            // 同時注文が競合した際のDBロック取得失敗（例: SQLiteのSQLITE_BUSY等）はユーザー側の入力ミスではないため、
            // 生の500エラーを見せず「もう一度お試しください」という穏当なメッセージでチェックアウト画面に戻す
            model.addAttribute("items", cartService.findByUser(user));
            model.addAttribute("total", cartService.totalPrice(user));
            model.addAttribute("paymentMethods", PaymentMethod.values());
            model.addAttribute("errorMessage", "ただいま混み合っています。しばらくしてからもう一度お試しください。");
            return "order/checkout";
        }
        // 注文確定に成功したら、作成された注文IDを付けて注文完了画面へリダイレクトする
        return "redirect:/checkout/complete/" + order.getId();
    }

    /**
     * GET /checkout/complete/{id}
     * 注文完了画面を表示する。所有者チェックにより他人の注文完了画面は閲覧できない。
     *
     * @param principal ログイン中ユーザーの情報
     * @param id        表示する注文のID（URLパス変数）
     * @param model     画面に渡すデータの入れ物
     * @return 所有者でない場合は "redirect:/"、それ以外は "order/complete"
     */
    @GetMapping("/checkout/complete/{id}")
    public String complete(@AuthenticationPrincipal SecurityUserDetails principal,
                            @PathVariable Long id, Model model) {
        // パス変数で指定された注文IDから注文エンティティを取得する
        Order order = orderService.findById(id);
        // 他人の注文完了ページをURL直打ちで閲覧できないよう所有者チェックを行う
        if (!order.getUser().getId().equals(principal.getUser().getId())) {
            // 所有者でなければトップページへリダイレクトする
            return "redirect:/";
        }
        // 所有者本人であれば注文情報を画面に渡す
        model.addAttribute("order", order);
        // order/complete.html（Thymeleafテンプレート）を表示する
        return "order/complete";
    }

    /**
     * GET /mypage
     * マイページのトップ画面を表示する。
     *
     * @param principal ログイン中ユーザーの情報
     * @param model     画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "mypage/index"
     */
    @GetMapping("/mypage")
    public String mypage(@AuthenticationPrincipal SecurityUserDetails principal, Model model) {
        // ログイン中ユーザーの情報を画面に渡す
        model.addAttribute("user", principal.getUser());
        // mypage/index.html（Thymeleafテンプレート）を表示する
        return "mypage/index";
    }

    /**
     * GET /mypage/orders
     * ログイン中ユーザーの注文履歴一覧を表示する。
     *
     * @param principal ログイン中ユーザーの情報
     * @param model     画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "order/history"
     */
    @GetMapping("/mypage/orders")
    public String orderHistory(@AuthenticationPrincipal SecurityUserDetails principal, Model model) {
        // ログイン中ユーザーに紐づく注文一覧を取得して画面に渡す（本人の注文のみを検索条件に含めているため所有者チェックは不要）
        model.addAttribute("orders", orderService.findByUser(principal.getUser()));
        // order/history.html（Thymeleafテンプレート）を表示する
        return "order/history";
    }

    /**
     * GET /mypage/orders/{id}
     * 注文詳細画面を表示する。所有者チェックにより他人の注文は閲覧できない。
     *
     * @param principal ログイン中ユーザーの情報
     * @param id        表示する注文のID（URLパス変数）
     * @param model     画面に渡すデータの入れ物
     * @return 所有者でない場合は "redirect:/mypage/orders"、それ以外は "order/detail"
     */
    @GetMapping("/mypage/orders/{id}")
    public String orderDetail(@AuthenticationPrincipal SecurityUserDetails principal,
                               @PathVariable Long id, Model model) {
        // パス変数で指定された注文IDから注文エンティティを取得する
        Order order = orderService.findById(id);
        // 他人の注文をURL直打ちで閲覧できないよう所有者チェックを行う
        if (!order.getUser().getId().equals(principal.getUser().getId())) {
            // 所有者でなければ注文履歴一覧へリダイレクトする
            return "redirect:/mypage/orders";
        }
        // 所有者本人であれば注文情報を画面に渡す
        model.addAttribute("order", order);
        // order/detail.html（Thymeleafテンプレート）を表示する
        return "order/detail";
    }

    /**
     * POST /mypage/orders/{id}/cancel
     * ログイン中ユーザー自身の注文をキャンセルする。
     * 所有者でない場合やキャンセル不可な状態の場合はサービス層で例外が発生し、
     * フラッシュメッセージとしてエラー内容を注文詳細画面に伝える。
     *
     * @param principal          ログイン中ユーザーの情報
     * @param id                 キャンセル対象の注文ID（URLパス変数）
     * @param redirectAttributes リダイレクト先の画面に一度だけ表示するフラッシュメッセージを設定するための機構
     * @return 注文詳細画面へのリダイレクト
     */
    @PostMapping("/mypage/orders/{id}/cancel")
    public String cancelOrder(@AuthenticationPrincipal SecurityUserDetails principal,
                               @PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // 注文の所有者チェックやキャンセル可否の判定はサービス層で行われる
            orderService.cancelByUser(id, principal.getUser());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 所有者不一致やキャンセル不可な状態などの場合、エラーメッセージをフラッシュ属性として
            // リダイレクト後の画面に一度だけ表示できるようにセットする
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        // 成功・失敗いずれの場合も注文詳細画面へリダイレクトする
        return "redirect:/mypage/orders/" + id;
    }
}
