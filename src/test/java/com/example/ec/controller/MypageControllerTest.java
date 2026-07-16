package com.example.ec.controller;

import com.example.ec.config.SecurityUserDetails;
import com.example.ec.entity.Role;
import com.example.ec.entity.User;
import com.example.ec.service.CategoryService;
import com.example.ec.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MypageController の "POST /mypage/password"（パスワード変更）に関するコントローラーテスト。
 * 新パスワードと確認用パスワードの一致チェック、現在パスワード誤り、入力バリデーションを検証する。
 */
@WebMvcTest(MypageController.class)
class MypageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private CategoryService categoryService;

    private SecurityUserDetails loginUser() {
        User user = new User();
        user.setId(1L);
        user.setName("テストユーザー");
        user.setEmail("user@example.com");
        user.setPassword("encoded-password");
        user.setRole(Role.ROLE_USER);
        user.setEnabled(true);
        return new SecurityUserDetails(user);
    }

    @Test
    void changePassword_新しいパスワードと確認用パスワードが一致しない場合はエラーになりサービスは呼ばれない() throws Exception {
        mockMvc.perform(post("/mypage/password")
                        .with(user(loginUser()))
                        .with(csrf())
                        .param("currentPassword", "current1234")
                        .param("newPassword", "newpassword1")
                        .param("confirmNewPassword", "differentpass"))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/password"))
                .andExpect(model().attributeHasFieldErrors("changePasswordForm", "confirmNewPassword"));

        verify(userService, never()).changePassword(anyLong(), anyString(), anyString());
    }

    @Test
    void changePassword_新しいパスワードと確認用パスワードが一致する場合はサービスを呼びマイページへリダイレクトする() throws Exception {
        mockMvc.perform(post("/mypage/password")
                        .with(user(loginUser()))
                        .with(csrf())
                        .param("currentPassword", "current1234")
                        .param("newPassword", "newpassword1")
                        .param("confirmNewPassword", "newpassword1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage?passwordChanged"));

        verify(userService).changePassword(1L, "current1234", "newpassword1");
    }

    @Test
    void changePassword_現在のパスワードが誤っている場合はエラーメッセージ付きで再表示する() throws Exception {
        doThrow(new IllegalArgumentException("現在のパスワードが正しくありません"))
                .when(userService).changePassword(anyLong(), anyString(), anyString());

        mockMvc.perform(post("/mypage/password")
                        .with(user(loginUser()))
                        .with(csrf())
                        .param("currentPassword", "wrongpassword")
                        .param("newPassword", "newpassword1")
                        .param("confirmNewPassword", "newpassword1"))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/password"))
                .andExpect(model().attribute("errorMessage", "現在のパスワードが正しくありません"));
    }

    @Test
    void changePassword_未入力項目があればバリデーションエラーとなりサービスは呼ばれない() throws Exception {
        mockMvc.perform(post("/mypage/password")
                        .with(user(loginUser()))
                        .with(csrf())
                        .param("currentPassword", "")
                        .param("newPassword", "short")
                        .param("confirmNewPassword", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/password"))
                .andExpect(model().attributeHasFieldErrors("changePasswordForm", "currentPassword", "newPassword", "confirmNewPassword"));

        verify(userService, never()).changePassword(anyLong(), anyString(), anyString());
    }

    @Test
    void changePassword_CSRFトークンがない場合は拒否されサービスは呼ばれない() throws Exception {
        mockMvc.perform(post("/mypage/password")
                        .with(user(loginUser()))
                        .param("currentPassword", "current1234")
                        .param("newPassword", "newpassword1")
                        .param("confirmNewPassword", "newpassword1"))
                .andExpect(status().isForbidden());

        verify(userService, never()).changePassword(anyLong(), anyString(), anyString());
    }
}
