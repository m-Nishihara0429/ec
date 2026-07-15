package com.example.ec.controller;

import com.example.ec.dto.ForgotPasswordForm;
import com.example.ec.dto.RegisterForm;
import com.example.ec.dto.ResetPasswordForm;
import com.example.ec.service.PasswordResetService;
import com.example.ec.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 会員登録・ログイン画面表示・パスワード再設定（忘れた場合の再発行フロー）を担当するコントローラー。
 * ログイン自体（認証処理）はSpring Securityのフィルターが担当するため、
 * このコントローラーはログイン画面の表示のみを行う。
 */
@Controller
public class AuthController {

    // ユーザー登録・更新を行うサービス
    private final UserService userService;
    // パスワード再設定用トークンの発行・検証・パスワード更新を行うサービス
    private final PasswordResetService passwordResetService;

    // コンストラクタインジェクションで各サービスを受け取る
    public AuthController(UserService userService, PasswordResetService passwordResetService) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * GET /login
     * ログイン画面を表示する。実際の認証処理はSpring Securityが行うため、
     * ここでは画面表示のみ行う。
     *
     * @return 表示するテンプレート名 "auth/login"
     */
    @GetMapping("/login")
    public String loginForm() {
        // auth/login.html（Thymeleafテンプレート）を表示する
        return "auth/login";
    }

    /**
     * GET /register
     * 会員登録フォームを表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "auth/register"
     */
    @GetMapping("/register")
    public String registerForm(Model model) {
        // 会員登録フォーム用の空DTOを画面に渡す
        model.addAttribute("registerForm", new RegisterForm());
        // auth/register.html（Thymeleafテンプレート）を表示する
        return "auth/register";
    }

    /**
     * POST /register
     * 入力内容をもとに会員登録を行う。
     * バリデーションエラーやメールアドレス重複などの業務エラー時は同じ画面を再表示する。
     *
     * @param form          送信された会員登録内容。{@code @Valid}で入力チェックを行う
     * @param bindingResult バリデーション結果
     * @param model         画面に渡すデータの入れ物（エラーメッセージ格納用）
     * @return エラー時は "auth/register"、成功時は登録完了パラメータ付きでログイン画面へリダイレクト
     */
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form,
                            BindingResult bindingResult,
                            Model model) {
        // 入力項目自体のバリデーションエラーがあれば同じ登録フォームを再表示する
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            // サービス層でユーザーを登録する（メールアドレス重複チェックなどはサービス層で行う）
            userService.register(form);
        } catch (IllegalArgumentException e) {
            // 業務ルール違反（重複メールアドレスなど）はエラーメッセージ付きで同じ画面に戻す
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/register";
        }
        // 登録成功後は登録完了をクエリパラメータで示しつつログイン画面へリダイレクトする
        return "redirect:/login?registered";
    }

    /**
     * GET /forgot-password
     * パスワードを忘れた場合の再設定申請フォームを表示する。
     *
     * @param model 画面に渡すデータの入れ物
     * @return 表示するテンプレート名 "auth/forgot_password"
     */
    @GetMapping("/forgot-password")
    public String forgotPasswordForm(Model model) {
        // 再設定申請フォーム用の空DTOを画面に渡す
        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        // auth/forgot_password.html（Thymeleafテンプレート）を表示する
        return "auth/forgot_password";
    }

    /**
     * POST /forgot-password
     * 入力されたメールアドレス宛のパスワード再設定用トークンを発行する。
     * 本来はメール送信するがメール基盤が無い学習用実装のため、再設定URLを画面に直接表示する。
     *
     * @param form          送信されたメールアドレスなど。{@code @Valid}で入力チェックを行う
     * @param bindingResult バリデーション結果
     * @param request       再設定用URLを組み立てるために現在のリクエスト情報を利用する
     * @param model         画面に渡すデータの入れ物（再設定URL・エラーメッセージ格納用）
     * @return いずれの場合も "auth/forgot_password"（同じ画面上に結果を表示する）
     */
    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
                                  BindingResult bindingResult,
                                  HttpServletRequest request,
                                  Model model) {
        // 入力項目自体のバリデーションエラーがあれば同じ画面を再表示する
        if (bindingResult.hasErrors()) {
            return "auth/forgot_password";
        }
        try {
            // 本来はメール送信するがメール基盤がないため、再設定用URLを画面に直接表示している（学習用の簡易実装）
            String token = passwordResetService.issueToken(form.getEmail());
            // リクエストURLからスキーム・ホスト・コンテキストパスまでを取り出し、ベースURLを組み立てる
            String baseUrl = request.getRequestURL().toString().replace(request.getRequestURI(), request.getContextPath());
            // 発行したトークンをクエリパラメータに付与した再設定用URLを画面に渡す
            model.addAttribute("resetUrl", baseUrl + "/reset-password?token=" + token);
        } catch (IllegalArgumentException e) {
            // 該当するユーザーが存在しない等の業務エラーはエラーメッセージ付きで同じ画面に戻す
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/forgot_password";
        }
        // 正常時もエラー時も同じテンプレートを表示し、Modelの内容によって表示を出し分ける
        return "auth/forgot_password";
    }

    /**
     * GET /reset-password
     * パスワード再設定フォームを表示する。トークンの有効性を事前にチェックする。
     *
     * @param token 再設定用トークン（メール等で送られてきたURLのクエリパラメータ）
     * @param model 画面に渡すデータの入れ物
     * @return トークンが無効な場合もフォームは同じ "auth/reset_password"（エラーメッセージのみ表示）
     */
    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam String token, Model model) {
        try {
            // トークンの有効性（存在・期限切れ・使用済みなど）を事前チェックし、
            // 無効な場合はフォームを出さずエラーメッセージのみ表示する
            passwordResetService.validateToken(token);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // トークンが無効な場合はエラーメッセージのみを画面に渡して終了する
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/reset_password";
        }
        // トークンが有効な場合はパスワード再設定フォーム用のDTOを作成する
        ResetPasswordForm form = new ResetPasswordForm();
        // フォーム送信時にどのトークンに対する再設定か分かるようトークンをセットしておく
        form.setToken(token);
        // フォームを画面に渡す
        model.addAttribute("resetPasswordForm", form);
        // auth/reset_password.html（Thymeleafテンプレート）を表示する
        return "auth/reset_password";
    }

    /**
     * POST /reset-password
     * トークンをもとにパスワードを再設定する。
     *
     * @param form          送信された新パスワードとトークン。{@code @Valid}で入力チェックを行う
     * @param bindingResult バリデーション結果
     * @param model         画面に渡すデータの入れ物（エラーメッセージ格納用）
     * @return エラー時は "auth/reset_password"、成功時は再設定完了パラメータ付きでログイン画面へリダイレクト
     */
    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
                                 BindingResult bindingResult,
                                 Model model) {
        // 入力項目自体のバリデーションエラーがあれば同じ画面を再表示する
        if (bindingResult.hasErrors()) {
            return "auth/reset_password";
        }
        try {
            // サービス層でトークンを検証しつつ新しいパスワードに更新する
            passwordResetService.resetPassword(form.getToken(), form.getPassword());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // トークンが無効・期限切れ等の業務エラーはエラーメッセージ付きで同じ画面に戻す
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/reset_password";
        }
        // 再設定完了をクエリパラメータで示しつつログイン画面へリダイレクトする
        return "redirect:/login?resetDone";
    }
}
