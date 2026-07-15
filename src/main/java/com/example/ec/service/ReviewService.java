package com.example.ec.service;

import com.example.ec.dto.ReviewForm;
import com.example.ec.entity.Product;
import com.example.ec.entity.Review;
import com.example.ec.entity.User;
import com.example.ec.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品レビューの参照・投稿・評価集計を行うサービスクラス。
 * 1ユーザーにつき1商品あたり1レビューまでという制約をここで実現しており、
 * 商品一覧などで使われる平均評価の一括取得（N+1問題対策）もこのクラスの責務。
 */
@Service // Springのサービス層Beanとして登録する
public class ReviewService {

    // レビューのデータアクセスを担当するリポジトリ
    private final ReviewRepository reviewRepository;

    // コンストラクタインジェクションでリポジトリを受け取る
    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    /**
     * 指定した商品に対するレビュー一覧を新しい順で取得する。
     *
     * @param product 対象商品
     * @return レビュー一覧（作成日時の降順）
     */
    public List<Review> findByProduct(Product product) {
        // 商品に紐づくレビューを新着順（作成日時の降順）で取得する
        return reviewRepository.findByProductOrderByCreatedAtDesc(product);
    }

    /**
     * 指定した商品の平均評価を計算する。
     *
     * @param product 対象商品
     * @return 平均評価（レビューが1件も無い場合は0.0）
     */
    public double averageRating(Product product) {
        // DB側で平均値を集計するクエリを実行する（レビューが無ければnullが返る）
        Double avg = reviewRepository.findAverageRatingByProduct(product);
        // nullの場合（レビュー0件）は0.0として扱い、呼び出し側でのnullチェックを不要にする
        return avg == null ? 0.0 : avg;
    }

    /**
     * 指定した商品のレビュー件数を取得する。
     *
     * @param product 対象商品
     * @return レビュー件数
     */
    public long reviewCount(Product product) {
        // 商品に紐づくレビュー件数をカウントする
        return reviewRepository.countByProduct(product);
    }

    /**
     * 複数商品の平均評価をまとめて取得する。
     * 商品一覧画面などで商品ごとに平均評価を都度クエリすると「N+1問題」（商品数分だけクエリが発行される）が
     * 発生するため、対象の商品IDをまとめて渡し、1回のクエリで全件の平均評価を取得する。
     *
     * @param productIds 平均評価を取得したい商品IDの集合
     * @return 商品ID→平均評価 のマップ（該当レビューが無い商品はキーに含まれない）
     */
    // 商品一覧など複数商品をまとめて表示する際に、N+1問題を避けるため一括で平均評価を取得する
    public Map<Long, Double> averageRatingsFor(Collection<Long> productIds) {
        // 結果を格納するマップ（商品ID→平均評価）を用意する
        Map<Long, Double> result = new HashMap<>();
        // リポジトリが返す集計結果（Object[]の1行が [商品ID, 平均評価] を表す）を1行ずつ処理する
        for (Object[] row : reviewRepository.findAverageRatingsForProductIds(productIds)) {
            // 0番目の要素が商品ID、1番目の要素が平均評価としてマップに詰める
            result.put((Long) row[0], (Double) row[1]);
        }
        // 集計済みのマップを返す
        return result;
    }

    /**
     * レビューを投稿（または更新）する。
     * 同一ユーザーが同一商品に既にレビューしていれば内容を上書きし（1商品につき1レビューまでの制約）、
     * まだレビューしていなければ新規にレビューを作成する。
     *
     * @param product 対象商品
     * @param user    投稿するユーザー
     * @param form    評価点・コメントなど投稿内容を保持するフォームDTO
     */
    // 同一ユーザーが同一商品に既にレビューしていれば上書き（1商品につき1レビューまで）、なければ新規作成する
    @Transactional // 検索と保存をまとめて1トランザクションにする
    public void submitReview(Product product, User user, ReviewForm form) {
        // 同じユーザー×同じ商品の既存レビューを検索し、無ければ空のReviewエンティティを新規生成する
        Review review = reviewRepository.findByProductAndUser(product, user)
                .orElseGet(Review::new);
        // 対象商品を設定（新規作成時に必要。更新時も同じ値が入るだけで無害）
        review.setProduct(product);
        // 投稿ユーザーを設定（新規作成時に必要）
        review.setUser(user);
        // フォームの評価点をエンティティに反映する
        review.setRating(form.getRating());
        // フォームのコメントをエンティティに反映する
        review.setComment(form.getComment());
        // 新規または更新済みのレビューを保存する
        reviewRepository.save(review);
    }
}
