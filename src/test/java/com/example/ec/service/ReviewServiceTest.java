package com.example.ec.service;

import com.example.ec.dto.ReviewForm;
import com.example.ec.entity.Product;
import com.example.ec.entity.Review;
import com.example.ec.entity.User;
import com.example.ec.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReviewService の単体テスト。
 * 1商品1ユーザー1レビューまで（再投稿は上書き）となる submitReview のロジックと、
 * 商品一覧のN+1回避に使う averageRatingsFor の集計変換ロジックを重点的に検証する。
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    private ReviewService reviewService;

    private Product product;
    private User user;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository);
        product = new Product();
        product.setId(10L);
        user = new User();
        user.setId(1L);
    }

    @Test
    void submitReview_未投稿なら新規レビューとして保存される() {
        when(reviewRepository.findByProductAndUser(product, user)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        ReviewForm form = new ReviewForm();
        form.setRating(5);
        form.setComment("最高でした");

        reviewService.submitReview(product, user, form);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        Review saved = captor.getValue();
        assertThat(saved.getProduct()).isEqualTo(product);
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getComment()).isEqualTo("最高でした");
    }

    @Test
    void submitReview_既存レビューがあれば新規作成せず内容を上書きする() {
        Review existing = new Review();
        existing.setId(100L);
        existing.setRating(2);
        existing.setComment("普通");
        when(reviewRepository.findByProductAndUser(product, user)).thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        ReviewForm form = new ReviewForm();
        form.setRating(4);
        form.setComment("見直しました");

        reviewService.submitReview(product, user, form);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        Review saved = captor.getValue();
        // 同じレコード（同一ID）が更新されるのであって、新規レコードにはならない
        assertThat(saved.getId()).isEqualTo(100L);
        assertThat(saved.getRating()).isEqualTo(4);
        assertThat(saved.getComment()).isEqualTo("見直しました");
    }

    @Test
    void averageRating_レビューが無ければ0を返す() {
        when(reviewRepository.findAverageRatingByProduct(product)).thenReturn(null);

        assertThat(reviewService.averageRating(product)).isEqualTo(0.0);
    }

    @Test
    void averageRating_レビューがあればDBの平均値をそのまま返す() {
        when(reviewRepository.findAverageRatingByProduct(product)).thenReturn(3.5);

        assertThat(reviewService.averageRating(product)).isEqualTo(3.5);
    }

    @Test
    void averageRatingsFor_複数商品の集計結果を商品IDごとのマップに変換する() {
        List<Object[]> rows = List.of(
                new Object[]{10L, 4.5},
                new Object[]{20L, 2.0}
        );
        when(reviewRepository.findAverageRatingsForProductIds(List.of(10L, 20L))).thenAnswer(inv -> rows);

        Map<Long, Double> result = reviewService.averageRatingsFor(List.of(10L, 20L));

        assertThat(result).containsEntry(10L, 4.5).containsEntry(20L, 2.0);
    }

    @Test
    void averageRatingsFor_レビューが無い商品はマップに含まれない() {
        when(reviewRepository.findAverageRatingsForProductIds(List.of(30L))).thenReturn(List.of());

        Map<Long, Double> result = reviewService.averageRatingsFor(List.of(30L));

        assertThat(result).doesNotContainKey(30L);
        assertThat(result).isEmpty();
    }
}
