package com.example.ec.controller;

import com.example.ec.config.CustomUserDetailsService;
import com.example.ec.config.LoginRateLimiter;
import com.example.ec.config.SecurityConfig;
import com.example.ec.service.CategoryService;
import com.example.ec.service.PasswordResetService;
import com.example.ec.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * AuthController の会員登録・パスワード再設定フローに関するコントローラーテスト。
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private LoginRateLimiter loginRateLimiter;

    @Test
    void register_正常系ではログイン画面へリダイレクトする() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("name", "テスト太郎")
                        .param("email", "newuser@example.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        verify(userService).register(any());
    }

    @Test
    void register_メールアドレスが重複している場合はエラーメッセージ付きで再表示する() throws Exception {
        doThrow(new IllegalArgumentException("このメールアドレスは既に登録されています"))
                .when(userService).register(any());

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("name", "テスト太郎")
                        .param("email", "dup@example.com")
                        .param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attribute("errorMessage", "このメールアドレスは既に登録されています"));
    }

    @Test
    void register_パスワードが8文字未満の場合はバリデーションエラーとなり登録処理は呼ばれない() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("name", "テスト太郎")
                        .param("email", "newuser@example.com")
                        .param("password", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("registerForm", "password"));
    }

    @Test
    void resetPassword_正常系ではログイン画面へリダイレクトする() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "valid-token")
                        .param("password", "newpassword1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?resetDone"));

        verify(passwordResetService).resetPassword("valid-token", "newpassword1");
    }

    @Test
    void resetPassword_トークンが無効な場合はエラーメッセージ付きで再表示する() throws Exception {
        doThrow(new IllegalArgumentException("無効なトークンです"))
                .when(passwordResetService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "invalid-token")
                        .param("password", "newpassword1"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset_password"))
                .andExpect(model().attribute("errorMessage", "無効なトークンです"));
    }

    @Test
    void resetPassword_トークンの期限切れなど業務エラーの場合もエラーメッセージ付きで再表示する() throws Exception {
        doThrow(new IllegalStateException("トークンの有効期限が切れています"))
                .when(passwordResetService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "expired-token")
                        .param("password", "newpassword1"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset_password"))
                .andExpect(model().attribute("errorMessage", "トークンの有効期限が切れています"));
    }
}
