package com.example.ec.controller;

import com.example.ec.config.SecurityUserDetails;
import com.example.ec.dto.ChangePasswordForm;
import com.example.ec.dto.ProfileForm;
import com.example.ec.entity.User;
import com.example.ec.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * マイページ（プロフィール編集・パスワード変更）を担当するコントローラー。
 * クラスに {@code @RequestMapping("/mypage")} が付与されているため、
 * 各メソッドのURLは「/mypage」を起点とした相対パスになる。
 * すべてログイン済みユーザー本人の情報のみを対象とし、
 * 対象ユーザーは常に {@code @AuthenticationPrincipal} から取得したログイン中ユーザーである。
 */
@Controller
@RequestMapping("/mypage")
public class MypageController {

    // ユーザー情報の更新・パスワード変更を行うサービス
    private final UserService userService;

    // コンストラクタインジェクションでサービスを受け取る
    public MypageController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /mypage/edit
     * プロフィール編集フォームを表示する。現在の名前を初期値としてセットする。
     *
     * @param principal ログイン中ユーザーの情報
     * @param model     画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "mypage/edit"
     */
    @GetMapping("/edit")
    public String editForm(@AuthenticationPrincipal SecurityUserDetails principal, Model model) {
        // ログイン中のユーザーエンティティを取り出す
        User user = principal.getUser();
        // 編集フォーム用のDTOを新規作成する
        ProfileForm form = new ProfileForm();
        // 現在の名前をフォームの初期値としてセットする
        form.setName(user.getName());
        // フォームを画面に渡す
        model.addAttribute("profileForm", form);
        // mypage/edit.html（Thymeleafテンプレート）を表示する
        return "mypage/edit";
    }

    /**
     * POST /mypage/edit
     * プロフィール（名前）を更新する。バリデーションエラー時は同じ画面を再表示する。
     *
     * @param principal     ログイン中ユーザーの情報
     * @param form          送信されたプロフィール内容。{@code @Valid}で入力チェックを行う
     * @param bindingResult バリデーション結果
     * @return エラー時は "mypage/edit"、成功時は更新完了パラメータ付きでマイページへリダイレクト
     */
    @PostMapping("/edit")
    public String edit(@AuthenticationPrincipal SecurityUserDetails principal,
                        @Valid @ModelAttribute("profileForm") ProfileForm form,
                        BindingResult bindingResult) {
        // バリデーションエラーがあれば同じ編集フォームを再表示する
        if (bindingResult.hasErrors()) {
            return "mypage/edit";
        }
        // サービス層を通じてDB上のユーザー名を更新する
        userService.updateName(principal.getUser().getId(), form.getName());
        // principal.getUser()はセッションのSecurityContextにキャッシュされたUserインスタンスで、
        // 通常は再ログインするまで更新されない。ここで直接書き換えないと、
        // マイページ等の画面がログインし直すまで古い名前を表示し続けてしまうため意図的に同期させている。
        principal.getUser().setName(form.getName());
        // 更新完了をクエリパラメータで示しつつマイページトップへリダイレクトする
        return "redirect:/mypage?updated";
    }

    /**
     * GET /mypage/password
     * パスワード変更フォームを表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "mypage/password"
     */
    @GetMapping("/password")
    public String passwordForm(Model model) {
        // パスワード変更フォーム用の空DTOを画面に渡す
        model.addAttribute("changePasswordForm", new ChangePasswordForm());
        // mypage/password.html（Thymeleafテンプレート）を表示する
        return "mypage/password";
    }

    /**
     * POST /mypage/password
     * ログイン中ユーザーのパスワードを変更する。
     * バリデーションエラーや現在のパスワード不一致などの場合は同じ画面を再表示する。
     *
     * @param principal     ログイン中ユーザーの情報
     * @param form          送信されたパスワード変更内容（現在のパスワード・新パスワード）。{@code @Valid}で入力チェックを行う
     * @param bindingResult バリデーション結果
     * @param model         画面に渡すデータの入れ物（エラーメッセージ格納用）
     * @return エラー時は "mypage/password"、成功時はパスワード変更完了パラメータ付きでマイページへリダイレクト
     */
    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal SecurityUserDetails principal,
                                  @Valid @ModelAttribute("changePasswordForm") ChangePasswordForm form,
                                  BindingResult bindingResult, Model model) {
        // 入力項目自体のバリデーションエラーがあれば同じ画面を再表示する
        if (bindingResult.hasErrors()) {
            return "mypage/password";
        }
        try {
            // サービス層で現在のパスワード照合・新パスワードへの変更を行う
            userService.changePassword(principal.getUser().getId(), form.getCurrentPassword(), form.getNewPassword());
        } catch (IllegalArgumentException e) {
            // 現在のパスワードが誤っている等、業務ルール違反はエラーメッセージ付きで同じ画面に戻す
            model.addAttribute("errorMessage", e.getMessage());
            return "mypage/password";
        }
        // 変更完了をクエリパラメータで示しつつマイページトップへリダイレクトする
        return "redirect:/mypage?passwordChanged";
    }
}
