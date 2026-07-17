package com.example.ec.service;

import com.example.ec.dto.RegisterForm;
import com.example.ec.entity.Role;
import com.example.ec.entity.User;
import com.example.ec.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService の単体テスト。特に updateRole / setEnabled の自己保護ロジック
 * （マスター管理者が自分自身を操作してロックアウトされるのを防ぐ）を重点的に検証する。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    private User actor;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
        actor = new User();
        actor.setId(1L);
        actor.setRole(Role.ROLE_MASTER);
    }

    @Test
    void register_メールアドレスが重複していれば拒否される() {
        RegisterForm form = new RegisterForm();
        form.setName("テスト太郎");
        form.setEmail("dup@example.com");
        form.setPassword("password123");
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(form))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_新規ユーザーは常にROLE_USERで作成されパスワードはハッシュ化される() {
        RegisterForm form = new RegisterForm();
        form.setName("テスト太郎");
        form.setEmail("new@example.com");
        form.setPassword("password123");
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.register(form);

        assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(saved.getPassword()).isEqualTo("hashed");
    }

    @Test
    void updateRole_自分自身が対象の場合は拒否される() {
        assertThatThrownBy(() -> userService.updateRole(actor.getId(), Role.ROLE_USER, actor))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRole_他人が対象なら変更される() {
        User target = new User();
        target.setId(2L);
        target.setRole(Role.ROLE_USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        userService.updateRole(2L, Role.ROLE_ADMIN, actor);

        assertThat(target.getRole()).isEqualTo(Role.ROLE_ADMIN);
        verify(userRepository).save(target);
    }

    @Test
    void updateRole_対象ユーザーが存在しない場合は拒否される() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateRole(99L, Role.ROLE_ADMIN, actor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setEnabled_自分自身が対象の場合は拒否される() {
        assertThatThrownBy(() -> userService.setEnabled(actor.getId(), false, actor))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_他人が対象なら無効化される() {
        User target = new User();
        target.setId(2L);
        target.setEnabled(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        userService.setEnabled(2L, false, actor);

        assertThat(target.isEnabled()).isFalse();
        verify(userRepository).save(target);
    }

    @Test
    void changePassword_現在のパスワードが一致しない場合は拒否される() {
        User target = new User();
        target.setId(2L);
        target.setPassword("hashed-old");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("wrong", "hashed-old")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(2L, "wrong", "newpassword"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_現在のパスワードが一致すれば新しいパスワードがハッシュ化されて保存される() {
        User target = new User();
        target.setId(2L);
        target.setPassword("hashed-old");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("correct", "hashed-old")).thenReturn(true);
        when(passwordEncoder.encode("newpassword")).thenReturn("hashed-new");

        userService.changePassword(2L, "correct", "newpassword");

        assertThat(target.getPassword()).isEqualTo("hashed-new");
        verify(userRepository).save(target);
    }
}
