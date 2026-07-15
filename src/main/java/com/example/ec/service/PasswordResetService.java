package com.example.ec.service;

import com.example.ec.entity.PasswordResetToken;
import com.example.ec.entity.User;
import com.example.ec.repository.PasswordResetTokenRepository;
import com.example.ec.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * パスワード再設定用トークンの発行・検証・失効を行うサービスクラス。
 * このプロジェクトではSMTPを設定していないため、メール送信の代わりに再設定用リンクを画面上に直接表示する。
 * トークンはUUIDのランダム文字列として発行し、有効期限とワンタイム性（1回使ったら削除）によって
 * 第三者による不正利用のリスクを下げている。
 */
@Service // Springのサービス層Beanとして登録する
public class PasswordResetService {

    // トークンの有効期限（分）。この時間を過ぎたトークンはvalidateToken内で失効扱いとなる
    private static final long EXPIRY_MINUTES = 30;

    // ユーザー情報の取得・更新を行うリポジトリ
    private final UserRepository userRepository;
    // パスワード再設定トークンの取得・保存・削除を行うリポジトリ
    private final PasswordResetTokenRepository tokenRepository;
    // パスワードのハッシュ化・照合を行うSpring Security標準のエンコーダー（内部的にBCrypt等を使用）
    private final PasswordEncoder passwordEncoder;

    // コンストラクタインジェクションで必要な依存を受け取る
    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * パスワード再設定用のトークンを新規発行する。
     * 発行前にそのユーザーの古いトークンを削除するため、あるユーザーについて常に有効なトークンは
     * 最大1つしか存在しない（＝古いリンクは新しいリンクを発行した時点で自動的に無効化される）。
     *
     * @param email 再設定を希望するユーザーのメールアドレス
     * @return 発行されたトークン文字列（呼び出し元の画面でリンクとしてそのまま表示される）
     * @throws IllegalArgumentException 指定したメールアドレスのユーザーが存在しない場合
     */
    // 新しいトークンを発行する前に、そのユーザーの古いトークンを削除しておく（同時に有効なトークンは常に1つだけ）
    // 戻り値のトークンはメール送信せず、呼び出し元の画面にそのままリンクとして表示される
    @Transactional // ユーザー検索・古いトークン削除・新規トークン保存を1つの整合した処理としてまとめる
    public String issueToken(String email) {
        // メールアドレスからユーザーを検索し、存在しなければ例外を投げる
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("このメールアドレスは登録されていません"));

        // そのユーザーが持つ古いトークンをすべて削除する（同時に有効なトークンを1つに保つため）
        tokenRepository.deleteByUser(user);

        // 新しいトークンエンティティを生成する
        PasswordResetToken resetToken = new PasswordResetToken();
        // トークンの持ち主を設定する
        resetToken.setUser(user);
        // ランダムなUUID文字列をトークン値として設定する（推測されにくい値にするため）
        resetToken.setToken(UUID.randomUUID().toString());
        // 現在時刻からEXPIRY_MINUTES分後を有効期限として設定する
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
        // 新しいトークンをDBに保存する
        tokenRepository.save(resetToken);
        // 発行したトークン文字列を呼び出し元に返す（画面にリンクとして表示するため）
        return resetToken.getToken();
    }

    /**
     * トークン文字列を検証し、有効であれば対応するトークンエンティティを返す。
     * 有効期限切れのトークンは検証時点でDBから削除し、以降は再利用できないようにする。
     *
     * @param token 検証するトークン文字列
     * @return 有効なパスワード再設定トークン
     * @throws IllegalArgumentException トークンが存在しない（無効な）場合
     * @throws IllegalStateException    トークンの有効期限が切れている場合
     */
    // 有効期限切れのトークンは検証時点で削除し、以降再利用できないようにする
    public PasswordResetToken validateToken(String token) {
        // トークン文字列でエンティティを検索し、見つからなければ例外を投げる
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("リンクが無効です。もう一度パスワード再設定をお試しください"));
        // 有効期限（expiresAt）が現在時刻より前＝期限切れかどうかを判定する
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            // 期限切れトークンはこの時点で削除し、二度と使えないようにする
            tokenRepository.delete(resetToken);
            // 呼び出し元には「期限切れ」であることを伝える例外を投げる
            throw new IllegalStateException("リンクの有効期限が切れています。もう一度パスワード再設定をお試しください");
        }
        // 有効なトークンであればそのまま返す
        return resetToken;
    }

    /**
     * トークンを検証したうえでパスワードを再設定する。
     * 変更後はトークンを削除し、同じリンクを再度使えないようにする（ワンタイムトークンとして扱う）。
     *
     * @param token       検証対象のトークン文字列
     * @param newPassword 設定する新しいパスワード（平文。ここでハッシュ化される）
     */
    // パスワード変更後はトークンを削除し、同じリンクを再度使えないようにする（使い切り＝ワンタイムトークン）
    @Transactional // トークン検証・パスワード更新・トークン削除を1つの整合した処理としてまとめる
    public void resetPassword(String token, String newPassword) {
        // トークンを検証する（無効・期限切れなら例外が投げられてここで処理は止まる）
        PasswordResetToken resetToken = validateToken(token);
        // トークンに紐づくユーザーを取得する
        User user = resetToken.getUser();
        // 新しいパスワードをBCrypt等でハッシュ化してからユーザーに設定する（平文のまま保存しないため）
        user.setPassword(passwordEncoder.encode(newPassword));
        // 変更したユーザー情報を保存する
        userRepository.save(user);
        // 使用済みのトークンを削除し、同じリンクの再利用を防ぐ
        tokenRepository.delete(resetToken);
    }
}
