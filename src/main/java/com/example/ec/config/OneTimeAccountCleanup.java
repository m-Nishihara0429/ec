package com.example.ec.config;

import com.example.ec.entity.Order;
import com.example.ec.entity.User;
import com.example.ec.repository.CartItemRepository;
import com.example.ec.repository.OrderRepository;
import com.example.ec.repository.PasswordResetTokenRepository;
import com.example.ec.repository.ReviewRepository;
import com.example.ec.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 【一時的な移行処理】指定した1アカウントとその注文履歴等を、起動時に一度だけ完全に削除する。
 *
 * <p>過去に実アカウント（実メールアドレス）をソースコードに直書きしてリポジトリに公開してしまっていたため、
 * そのアカウントをコード・Git履歴からは削除したが、本番DBには既にそのアカウントのレコード（および
 * 注文履歴等）が残っている。それを削除するための使い捨てのクリーンアップ処理。</p>
 *
 * <p>削除対象のメールアドレスは、このクラス自身にも一切書かない（再度リポジトリに載せないため）。
 * 環境変数 {@code CLEANUP_TARGET_EMAIL} からのみ受け取り、Renderのダッシュボードで手動設定する
 * （未設定時は何もしない安全なデフォルト = 空文字）。役目を終えたら、このクラスと
 * {@code app.cleanup.target-email} プロパティ、環境変数ごと削除すること。</p>
 */
@Component
@Slf4j
public class OneTimeAccountCleanup implements CommandLineRunner {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    // 削除対象アカウントのメールアドレス。未設定（空文字）ならこのクラスは何もしない
    private final String targetEmail;

    public OneTimeAccountCleanup(UserRepository userRepository, OrderRepository orderRepository,
                                  CartItemRepository cartItemRepository, ReviewRepository reviewRepository,
                                  PasswordResetTokenRepository passwordResetTokenRepository,
                                  @Value("${app.cleanup.target-email:}") String targetEmail) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.reviewRepository = reviewRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.targetEmail = targetEmail;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // 環境変数未設定（空文字）なら何もしない。ローカル開発環境や、削除が既に完了した後の
        // 再起動でも安全に無害な状態を保つ
        if (targetEmail == null || targetEmail.isBlank()) {
            return;
        }

        Optional<User> maybeUser = userRepository.findByEmail(targetEmail);
        if (maybeUser.isEmpty()) {
            // 既に削除済み、または対象が存在しない場合はログだけ残して終了（べき等）
            log.info("OneTimeAccountCleanup: 削除対象アカウントは見つかりませんでした（既に削除済みの可能性）");
            return;
        }

        User user = maybeUser.get();
        Long userId = user.getId();

        // パスワード再設定トークンを削除
        passwordResetTokenRepository.deleteByUser(user);

        // 投稿済みレビューを削除
        reviewRepository.deleteByUser(user);

        // カート内商品を削除
        cartItemRepository.deleteByUser(user);

        // 注文（明細はOrder側のcascade+orphanRemovalにより一緒に削除される）を削除
        List<Order> orders = orderRepository.findByUserOrderByCreatedAtDesc(user);
        orderRepository.deleteAll(orders);

        // 最後にユーザー本体を削除
        userRepository.delete(user);

        log.info("OneTimeAccountCleanup: アカウント(userId={})と注文{}件を削除しました",
                userId, orders.size());
    }
}
