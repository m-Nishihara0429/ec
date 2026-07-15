package com.example.ec.controller;

import com.example.ec.config.SecurityUserDetails;
import com.example.ec.entity.CartItem;
import com.example.ec.entity.Product;
import com.example.ec.entity.User;
import com.example.ec.service.CartService;
import com.example.ec.service.ProductService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * カート機能（一覧表示・商品追加・数量変更・削除）を担当するコントローラー。
 * すべてのハンドラはログイン済みユーザーを前提としており、
 * {@code @AuthenticationPrincipal} でログイン中のユーザー情報を取得して処理する。
 * クラスに {@code @RequestMapping("/cart")} が付与されているため、
 * 各メソッドのURLは「/cart」を起点とした相対パスになる。
 */
@Controller
@RequestMapping("/cart")
public class CartController {

    // カートの追加・更新・削除・合計計算などを行うサービス
    private final CartService cartService;
    // 商品IDから商品エンティティを取得するためのサービス
    private final ProductService productService;

    // コンストラクタインジェクションで各サービスを受け取る（Spring Bootが自動的に注入する）
    public CartController(CartService cartService, ProductService productService) {
        this.cartService = cartService;
        this.productService = productService;
    }

    /**
     * GET /cart
     * ログイン中ユーザーのカート一覧画面を表示する。
     *
     * @param principal ログイン中ユーザーの情報（Spring Securityが自動的に注入する）
     * @param model     画面（Thymeleafテンプレート）に渡すデータの入れ物
     * @return 表示するテンプレート名 "cart/cart"
     */
    @GetMapping
    public String view(@AuthenticationPrincipal SecurityUserDetails principal, Model model) {
        // ログイン中のユーザーエンティティを取り出す
        User user = principal.getUser();
        // そのユーザーが持つカート内商品の一覧を取得する
        List<CartItem> items = cartService.findByUser(user);
        // 画面に渡すためカート内商品一覧をModelに詰める
        model.addAttribute("items", items);
        // カート合計金額もあわせて画面に渡す
        model.addAttribute("total", cartService.totalPrice(user));
        // cart/cart.html（Thymeleafテンプレート）を表示する
        return "cart/cart";
    }

    /**
     * POST /cart/add
     * 指定した商品を指定した数量だけカートに追加する。
     *
     * @param principal ログイン中ユーザーの情報
     * @param productId 追加する商品のID（フォームのリクエストパラメータ）
     * @param quantity  追加する数量。未指定の場合は1個として扱う
     * @return カート一覧へリダイレクト（"redirect:/cart"）
     */
    @PostMapping("/add")
    public String add(@AuthenticationPrincipal SecurityUserDetails principal,
                       @RequestParam Long productId,
                       @RequestParam(defaultValue = "1") int quantity) {
        // 追加対象の商品IDから商品エンティティを取得する
        Product product = productService.findById(productId);
        // ログイン中ユーザーのカートに、取得した商品を指定数量分追加する
        cartService.addToCart(principal.getUser(), product, quantity);
        // 処理後はカート一覧画面へリダイレクトする（PRGパターンで二重送信を防ぐ）
        return "redirect:/cart";
    }

    /**
     * POST /cart/update
     * カート内商品の数量を変更する。0以下が指定された場合は削除として扱う。
     *
     * @param cartItemId 数量を更新するカート内商品のID
     * @param quantity   変更後の数量
     * @return カート一覧へリダイレクト（"redirect:/cart"）
     */
    @PostMapping("/update")
    public String update(@RequestParam Long cartItemId, @RequestParam int quantity) {
        // 数量を0以下に更新しようとした場合は削除として扱う
        if (quantity <= 0) {
            // 数量が0以下ならカートからそのアイテムを削除する
            cartService.removeItem(cartItemId);
        } else {
            // それ以外の場合は指定された数量に更新する
            cartService.updateQuantity(cartItemId, quantity);
        }
        // 処理後はカート一覧画面へリダイレクトする
        return "redirect:/cart";
    }

    /**
     * POST /cart/remove
     * カートから指定したカート内商品を削除する。
     *
     * @param cartItemId 削除するカート内商品のID
     * @return カート一覧へリダイレクト（"redirect:/cart"）
     */
    @PostMapping("/remove")
    public String remove(@RequestParam Long cartItemId) {
        // 指定されたカート内商品IDのレコードをカートから削除する
        cartService.removeItem(cartItemId);
        // 処理後はカート一覧画面へリダイレクトする
        return "redirect:/cart";
    }
}
