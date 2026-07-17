package com.example.ec.service;

import com.example.ec.entity.PasswordResetToken;
import com.example.ec.entity.User;
import com.example.ec.repository.PasswordResetTokenRepository;
import com.example.ec.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PasswordResetService の単体テスト。
 * トークンの発行（古いトークンの失効込み）・有効期限切れの検知と削除・
 * 使い切り（再設定成功後の削除）という、このクラスの中核となる不変条件を検証する。
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordResetService passwordResetService;

    private User user;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(userRepository, tokenRepository, passwordEncoder);
        user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
    }

    @Test
    void issueToken_未登録のメールアドレスは拒否される() {
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.issueToken("unknown@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void issueToken_発行前に同一ユーザーの古いトークンを削除してから新規トークンを保存する() {
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String token = passwordResetService.issueToken("user@example.com");

        assertThat(token).isNotBlank();
        verify(tokenRepository).deleteByUser(user);
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getToken()).isEqualTo(token);
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void validateToken_存在しないトークンは拒否される() {
        when(tokenRepository.findByToken("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.validateToken("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateToken_期限切れトークンは削除されたうえで拒否される() {
        PasswordResetToken expired = new PasswordResetToken();
        expired.setUser(user);
        expired.setToken("expired-token");
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> passwordResetService.validateToken("expired-token"))
                .isInstanceOf(IllegalStateException.class);
        verify(tokenRepository).delete(expired);
    }

    @Test
    void validateToken_有効なトークンはそのまま返される() {
        PasswordResetToken valid = new PasswordResetToken();
        valid.setUser(user);
        valid.setToken("valid-token");
        valid.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(valid));

        PasswordResetToken result = passwordResetService.validateToken("valid-token");

        assertThat(result).isSameAs(valid);
        verify(tokenRepository, never()).delete(any());
    }

    @Test
    void resetPassword_成功時は新パスワードをハッシュ化して保存しトークンを使い切りとして削除する() {
        PasswordResetToken valid = new PasswordResetToken();
        valid.setUser(user);
        valid.setToken("valid-token");
        valid.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(valid));
        when(passwordEncoder.encode("newpassword1")).thenReturn("hashed-new");

        passwordResetService.resetPassword("valid-token", "newpassword1");

        assertThat(user.getPassword()).isEqualTo("hashed-new");
        verify(userRepository).save(user);
        verify(tokenRepository, times(1)).delete(valid);
    }

    @Test
    void resetPassword_期限切れトークンではパスワードは変更されない() {
        PasswordResetToken expired = new PasswordResetToken();
        expired.setUser(user);
        expired.setToken("expired-token");
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> passwordResetService.resetPassword("expired-token", "newpassword1"))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }
}
