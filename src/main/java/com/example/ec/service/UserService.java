package com.example.ec.service;

import com.example.ec.dto.RegisterForm;
import com.example.ec.entity.Role;
import com.example.ec.entity.User;
import com.example.ec.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ユーザーの登録・氏名変更・パスワード変更を行うサービスクラス。
 * パスワードは常にPasswordEncoder（BCrypt等）でハッシュ化してから保存し、
 * 平文のまま扱う・保存することがないようにしている点が重要な責務。
 */
@Service // Springのサービス層Beanとして登録する
// @Slf4j はLombokのアノテーションで、"log"という名前のLoggerフィールドを自動生成する。
// パスワードなど機密値そのものは出力せず、誰が・何をしたかのみを記録する
@Slf4j
public class UserService {

    // ユーザー情報の取得・保存を担当するリポジトリ
    private final UserRepository userRepository;
    // パスワードのハッシュ化・照合を行うSpring Security標準のエンコーダー
    private final PasswordEncoder passwordEncoder;

    // コンストラクタインジェクションで必要な依存を受け取る
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 登録済みユーザーの総数を取得する（主に管理画面のダッシュボード表示用）。
     *
     * @return ユーザーの総数
     */
    public long count() {
        // リポジトリのcountをそのまま呼び出す
        return userRepository.count();
    }

    /**
     * 新規ユーザーを登録する。
     * メールアドレスの重複登録を防ぎ、新規ユーザーは常に一般ユーザー権限（ROLE_USER）で作成される
     * （管理者権限は別途手動で付与する運用を想定している）。
     *
     * @param form 登録フォーム（氏名・メールアドレス・パスワードなどの入力値）
     * @return 保存された新規ユーザー
     * @throws IllegalArgumentException 指定したメールアドレスが既に登録済みの場合
     */
    // メールアドレスの重複登録を防ぎ、新規ユーザーは常に一般ユーザー権限（ROLE_USER）で作成する
    @Transactional // 重複チェックと保存を1つの整合した処理としてまとめる
    public User register(RegisterForm form) {
        // 前後の空白を除去する（メールアドレスは大文字小文字を区別しないため、
        // 重複チェックはexistsByEmailIgnoreCaseで行い"Alice@example.com"と"alice@example.com"を
        // 同一アドレスとして扱う。表示用に元の入力の大文字小文字はそのまま保持する）
        String normalizedEmail = form.getEmail().trim();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.warn("会員登録失敗（メールアドレス重複）: email={}", normalizedEmail);
            // 既に登録済みであれば例外を投げて処理を中断する
            throw new IllegalArgumentException("このメールアドレスは既に登録されています");
        }
        // 新規ユーザーエンティティを生成する
        User user = new User();
        // フォームの氏名を設定する
        user.setName(form.getName());
        // フォームのメールアドレスを設定する
        user.setEmail(normalizedEmail);
        // パスワードは平文のまま保存せず、必ずハッシュ化してから設定する
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        // 新規登録ユーザーには常に一般ユーザー権限を付与する（管理者は別途手動で昇格させる想定）
        user.setRole(Role.ROLE_USER);
        // ユーザーをDBに保存し、保存後（IDが採番された）のエンティティを返す
        User saved = userRepository.save(user);
        log.info("会員登録: userId={}, email={}", saved.getId(), saved.getEmail());
        return saved;
    }

    /**
     * ユーザーの氏名を変更する。
     *
     * @param userId 変更対象のユーザーID
     * @param name   新しい氏名
     * @throws IllegalArgumentException 指定したIDのユーザーが存在しない場合
     */
    @Transactional // 検索と更新をまとめて1トランザクションにする
    public void updateName(Long userId, String name) {
        // IDでユーザーを検索し、存在しなければ例外を投げる
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + userId));
        // 氏名を新しい値に更新する
        user.setName(name);
        // 変更を保存する
        userRepository.save(user);
    }

    /**
     * ユーザーのパスワードを変更する。
     * なりすましによる変更を防ぐため、新しいパスワードを設定する前に現在のパスワードが
     * 一致するかどうかを検証する（本人以外が現在のパスワードを知らずに変更することはできない）。
     *
     * @param userId          変更対象のユーザーID
     * @param currentPassword 現在のパスワード（平文。検証にのみ使う）
     * @param newPassword     新しいパスワード（平文。ハッシュ化して保存する）
     * @throws IllegalArgumentException ユーザーが存在しない場合、または現在のパスワードが一致しない場合
     */
    // なりすましによる変更を防ぐため、新しいパスワードを設定する前に現在のパスワードが一致するか検証する
    @Transactional // 検索・検証・更新をまとめて1トランザクションにする
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        // IDでユーザーを検索し、存在しなければ例外を投げる
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + userId));
        // 入力された現在のパスワード（平文）が、DBに保存されているハッシュ値と一致するかを検証する
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("パスワード変更失敗（現在のパスワード不一致）: userId={}", userId);
            // 一致しなければ本人確認に失敗したとみなし例外を投げる
            throw new IllegalArgumentException("現在のパスワードが正しくありません");
        }
        // 検証をパスしたら新しいパスワードをハッシュ化して設定する
        user.setPassword(passwordEncoder.encode(newPassword));
        // 変更を保存する
        userRepository.save(user);
        log.info("パスワード変更: userId={}", userId);
    }

    /**
     * 全会員を登録日時の新しい順で取得する（マスター管理者の会員管理画面向け）。
     *
     * @return 全ユーザー一覧（登録日時の降順）
     */
    public List<User> findAll() {
        // 全ユーザーを新着順で取得する
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 指定した会員のロールを変更する（マスター管理者専用の操作）。
     * 自分自身のロールを変更すると、操作中に自分がマスター権限を失って
     * 以降の会員管理操作ができなくなる（ロックアウトされる）おそれがあるため、
     * 操作者自身のロールは変更できないようにしている。
     *
     * @param targetUserId ロールを変更する対象の会員ID
     * @param newRole      新しく設定するロール
     * @param actor        この操作を行っているマスター管理者本人
     * @throws IllegalArgumentException 対象ユーザーが存在しない場合
     * @throws IllegalStateException    自分自身のロールを変更しようとした場合
     */
    @Transactional // 検索・自己保護チェック・更新をまとめて1トランザクションにする
    public void updateRole(Long targetUserId, Role newRole, User actor) {
        // 自分自身が対象の場合は、ロックアウトを防ぐため操作を拒否する
        if (targetUserId.equals(actor.getId())) {
            throw new IllegalStateException("自分自身のロールは変更できません");
        }
        // IDでユーザーを検索し、存在しなければ例外を投げる
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + targetUserId));
        // ロールを新しい値に更新する
        target.setRole(newRole);
        // 変更を保存する
        userRepository.save(target);
        log.info("会員ロール変更: targetUserId={}, newRole={}, actorUserId={}", targetUserId, newRole, actor.getId());
    }

    /**
     * 指定した会員の有効/無効を切り替える（マスター管理者専用の操作）。
     * 無効化されたユーザーはSecurityUserDetails#isEnabledがfalseを返すようになり、ログインできなくなる。
     * ロールと同様、自分自身を無効化してログインできなくなる事態を防ぐため、自分自身は対象にできない。
     *
     * @param targetUserId 有効/無効を切り替える対象の会員ID
     * @param enabled      trueなら有効化、falseなら無効化
     * @param actor        この操作を行っているマスター管理者本人
     * @throws IllegalArgumentException 対象ユーザーが存在しない場合
     * @throws IllegalStateException    自分自身を無効化しようとした場合
     */
    @Transactional // 検索・自己保護チェック・更新をまとめて1トランザクションにする
    public void setEnabled(Long targetUserId, boolean enabled, User actor) {
        // 自分自身が対象の場合は、ロックアウトを防ぐため操作を拒否する
        if (targetUserId.equals(actor.getId())) {
            throw new IllegalStateException("自分自身を無効化することはできません");
        }
        // IDでユーザーを検索し、存在しなければ例外を投げる
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + targetUserId));
        // 有効/無効フラグを更新する
        target.setEnabled(enabled);
        // 変更を保存する
        userRepository.save(target);
        log.info("会員有効/無効切り替え: targetUserId={}, enabled={}, actorUserId={}", targetUserId, enabled, actor.getId());
    }
}
