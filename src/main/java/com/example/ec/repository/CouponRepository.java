package com.example.ec.repository;

import com.example.ec.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * クーポン(Coupon)情報を管理するリポジトリ。
 * 利用回数の上限チェック＋加算は、在庫の原子的な増減（ProductRepository）と同様に
 * 1本のUPDATE文で行い、複数ユーザーが同時に同じクーポンを使い切る際の競合を防ぐ。
 */
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // クーポンコードでの検索（コードはユニークなため最大1件）
    Optional<Coupon> findByCode(String code);

    // 管理画面の一覧表示用。新しい順で全件取得する
    List<Coupon> findAllByOrderByCreatedAtDesc();

    /**
     * 利用回数上限に達していない場合のみ、原子的に利用回数を1加算する。
     * WHERE句に (usageLimit IS NULL OR usedCount < usageLimit) を含めることで、
     * 「読み取り→判定→書き込み」を分けた場合に起こりうる、複数注文が同時に
     * 同じクーポンを使い切ろうとした際の上限超過（レースコンディション）を防ぐ。
     *
     * @param id クーポンID
     * @return 更新できた行数（0なら利用回数上限に達しており加算できなかった）
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount + 1 " +
            "WHERE c.id = :id AND (c.usageLimit IS NULL OR c.usedCount < c.usageLimit)")
    int incrementUsageIfAvailable(@Param("id") Long id);
}
