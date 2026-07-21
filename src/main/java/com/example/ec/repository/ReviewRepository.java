package com.example.ec.repository;

import com.example.ec.entity.Product;
import com.example.ec.entity.Review;
import com.example.ec.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 商品レビュー(評価・コメント)を管理するリポジトリ。
 * 商品詳細ページの表示や平均評価の集計に利用する。
 * JpaRepository<Review, Long> を継承することで、
 * save・findById・findAll・deleteなどの基本的なCRUD操作が自動的に使えるようになる。
 * Review はエンティティ型、Long は主キー(id)の型を表す。
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 「productカラムが一致するReviewを全件検索し、createdAt(投稿日時)の降順(新しい順)に並べる」
    // クエリメソッド。商品詳細ページにレビュー一覧を新着順で表示するために使用する。
    // spring.jpa.open-in-view=false のため、テンプレート側でreview.user（LAZY）を参照する
    // 時点ではセッションが閉じている。@EntityGraphでuserを同じクエリで一括取得しておく。
    @EntityGraph(attributePaths = "user")
    List<Review> findByProductOrderByCreatedAtDesc(Product product);

    // 1ユーザーにつき1商品1件のレビューという前提のもと、既存レビューの有無・編集対象を取得する
    // 「productとuserの両方が一致するReviewを1件検索する」クエリメソッド。
    // 存在すれば編集画面へ、存在しなければ新規投稿画面へ、といった分岐に使用する。
    Optional<Review> findByProductAndUser(Product product, User user);

    // 商品詳細ページに表示する平均評価(星の数)を算出する
    // @Query によりJPQL(オブジェクト指向のSQLのようなもの)を直接記述している。
    // 「指定されたproductに紐づくRatingの平均値」を計算するクエリで、
    // @Param("product") によりメソッド引数 product をJPQL内の :product に束縛する。
    @Query("select avg(r.rating) from Review r where r.product = :product")
    Double findAverageRatingByProduct(@Param("product") Product product);

    // 「productカラムが一致するReviewの件数」を数えるクエリメソッド。
    // 商品詳細ページに「レビュー件数」を表示するために使用する。
    long countByProduct(Product product);

    // 商品一覧など複数商品をまとめて表示する際に、N+1問題を避けて一括で平均評価を取得する
    // 戻り値は [商品ID, 平均評価] の配列のリストとなる
    // @Query によるJPQLで、productIds に含まれる商品ごとにgroup by(グループ化)し、
    // 商品IDと平均評価のペアを一括取得する。@Param("productIds") によりメソッド引数
    // productIds をJPQL内の :productIds に束縛する。商品を1件ずつ問い合わせる代わりに
    // 1回のクエリで済ませることで、パフォーマンス上の問題(N+1問題)を回避している。
    @Query("select r.product.id, avg(r.rating) from Review r where r.product.id in :productIds group by r.product.id")
    List<Object[]> findAverageRatingsForProductIds(@Param("productIds") Collection<Long> productIds);
}
