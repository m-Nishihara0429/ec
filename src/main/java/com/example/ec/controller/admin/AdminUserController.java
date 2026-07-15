package com.example.ec.controller.admin;

import com.example.ec.config.SecurityUserDetails;
import com.example.ec.entity.Role;
import com.example.ec.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * マスター管理者専用：会員（User）のロール変更・有効/無効化を担当するコントローラー。
 * クラスに {@code @RequestMapping("/admin/users")} が付与されているため、
 * 各メソッドのURLは「/admin/users」を起点とした相対パスになる。
 * このパス配下へのアクセス制御（ROLE_MASTERが必要であること）は
 * {@link com.example.ec.config.SecurityConfig} 側で行われる想定。
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    // 会員一覧取得・ロール変更・有効/無効化を行うサービス
    private final UserService userService;

    // コンストラクタインジェクションでサービスを受け取る
    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /admin/users
     * 会員一覧画面を表示する。
     *
     * @param principal ログイン中のマスター管理者本人（自分自身の行を画面側で判別するために使う）
     * @param model     画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/users"
     */
    @GetMapping
    public String list(@AuthenticationPrincipal SecurityUserDetails principal, Model model) {
        // 全会員一覧（新着順）を画面に渡す
        model.addAttribute("users", userService.findAll());
        // ロール変更フォームのプルダウンに使う選択肢一覧を画面に渡す
        model.addAttribute("roles", Role.values());
        // ログイン中本人のIDを渡し、画面側で「自分自身の行だけロール変更・無効化操作を隠す」判定に使う
        model.addAttribute("currentUserId", principal.getUser().getId());
        // admin/users.html（Thymeleafテンプレート）を表示する
        return "admin/users";
    }

    /**
     * POST /admin/users/{id}/role
     * 指定した会員のロールを変更する。自分自身は対象にできない（サービス層でチェック）。
     *
     * @param id        対象の会員ID
     * @param role      変更後のロール（フォームのリクエストパラメータ）
     * @param principal ログイン中のマスター管理者本人
     * @param model     エラーメッセージ表示用
     * @return 成功時は会員一覧へリダイレクト、失敗時は一覧を再表示
     */
    @PostMapping("/{id}/role")
    public String updateRole(@PathVariable Long id, @RequestParam Role role,
                              @AuthenticationPrincipal SecurityUserDetails principal, Model model) {
        try {
            // サービス層でロールを更新する（自分自身が対象の場合はここで例外が投げられる）
            userService.updateRole(id, role, principal.getUser());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 自己保護違反や対象不在などの業務ルール違反はエラーメッセージ付きで一覧を再表示する
            return listWithError(e.getMessage(), principal, model);
        }
        // 変更後は会員一覧画面へリダイレクトする（PRGパターンで二重送信を防ぐ）
        return "redirect:/admin/users";
    }

    /**
     * POST /admin/users/{id}/enabled
     * 指定した会員の有効/無効を切り替える。自分自身は対象にできない（サービス層でチェック）。
     *
     * @param id        対象の会員ID
     * @param enabled   変更後の有効状態（フォームのリクエストパラメータ、true=有効化 / false=無効化）
     * @param principal ログイン中のマスター管理者本人
     * @param model     エラーメッセージ表示用
     * @return 成功時は会員一覧へリダイレクト、失敗時は一覧を再表示
     */
    @PostMapping("/{id}/enabled")
    public String updateEnabled(@PathVariable Long id, @RequestParam boolean enabled,
                                 @AuthenticationPrincipal SecurityUserDetails principal, Model model) {
        try {
            // サービス層で有効/無効を更新する（自分自身が対象の場合はここで例外が投げられる）
            userService.setEnabled(id, enabled, principal.getUser());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 自己保護違反や対象不在などの業務ルール違反はエラーメッセージ付きで一覧を再表示する
            return listWithError(e.getMessage(), principal, model);
        }
        // 変更後は会員一覧画面へリダイレクトする
        return "redirect:/admin/users";
    }

    /**
     * ロール変更・有効/無効化操作が業務ルール違反で失敗した際に、
     * エラーメッセージ付きで会員一覧画面を再表示するための共通処理。
     *
     * @param errorMessage 表示するエラーメッセージ
     * @param principal    ログイン中のマスター管理者本人
     * @param model        画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "admin/users"
     */
    private String listWithError(String errorMessage, SecurityUserDetails principal, Model model) {
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("users", userService.findAll());
        model.addAttribute("roles", Role.values());
        model.addAttribute("currentUserId", principal.getUser().getId());
        return "admin/users";
    }
}
