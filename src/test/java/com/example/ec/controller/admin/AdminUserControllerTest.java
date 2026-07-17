package com.example.ec.controller.admin;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * AdminUserController（会員管理・マスター管理者専用）のコントローラーテスト。
 * UserService側の自己保護ロジック（自分自身のロール変更・無効化を拒否）が
 * IllegalStateException/IllegalArgumentExceptionとして投げられた場合に、
 * コントローラーがエラーメッセージ付きで一覧を再表示することを重点的に検証する。
 */
@WebMvcTest(AdminUserController.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private CategoryService categoryService;

    private SecurityUserDetails masterUser() {
        User user = new User();
        user.setId(1L);
        user.setName("マスター管理者");
        user.setEmail("master@example.com");
        user.setPassword("encoded-password");
        user.setRole(Role.ROLE_MASTER);
        user.setEnabled(true);
        return new SecurityUserDetails(user);
    }

    @Test
    void updateRole_他人が対象なら成功し一覧へリダイレクトする() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/role", 2L)
                        .with(user(masterUser()))
                        .with(csrf())
                        .param("role", "ROLE_ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userService).updateRole(eq(2L), eq(Role.ROLE_ADMIN), any(User.class));
    }

    @Test
    void updateRole_自分自身が対象だとサービス層の例外を受けてエラーメッセージ付きで一覧を再表示する() throws Exception {
        doThrow(new IllegalStateException("自分自身のロールは変更できません"))
                .when(userService).updateRole(eq(1L), any(Role.class), any(User.class));

        mockMvc.perform(post("/admin/users/{id}/role", 1L)
                        .with(user(masterUser()))
                        .with(csrf())
                        .param("role", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attribute("errorMessage", "自分自身のロールは変更できません"));
    }

    @Test
    void updateRole_対象ユーザーが存在しない場合もエラーメッセージ付きで一覧を再表示する() throws Exception {
        doThrow(new IllegalArgumentException("ユーザーが見つかりません: 99"))
                .when(userService).updateRole(eq(99L), any(Role.class), any(User.class));

        mockMvc.perform(post("/admin/users/{id}/role", 99L)
                        .with(user(masterUser()))
                        .with(csrf())
                        .param("role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attribute("errorMessage", "ユーザーが見つかりません: 99"));
    }

    @Test
    void updateEnabled_他人が対象なら成功し一覧へリダイレクトする() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/enabled", 2L)
                        .with(user(masterUser()))
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userService).setEnabled(eq(2L), eq(false), any(User.class));
    }

    @Test
    void updateEnabled_自分自身が対象だとエラーメッセージ付きで一覧を再表示しサービスの副作用は保存されない() throws Exception {
        doThrow(new IllegalStateException("自分自身を無効化することはできません"))
                .when(userService).setEnabled(eq(1L), eq(false), any(User.class));

        mockMvc.perform(post("/admin/users/{id}/enabled", 1L)
                        .with(user(masterUser()))
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attribute("errorMessage", "自分自身を無効化することはできません"));
    }

    @Test
    void updateRole_CSRFトークンが無い場合は拒否されサービスは呼ばれない() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/role", 2L)
                        .with(user(masterUser()))
                        .param("role", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateRole(any(), any(), any());
    }
}
