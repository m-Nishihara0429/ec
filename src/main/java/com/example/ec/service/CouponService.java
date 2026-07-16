package com.example.ec.service;

import com.example.ec.dto.CouponForm;
import com.example.ec.entity.Coupon;
import com.example.ec.entity.DiscountType;
import com.example.ec.repository.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * クーポン（割引）の参照・登録・更新・削除、およびチェックアウト時の適用可否判定を行うサービスクラス。
 *
 * <p>利用回数上限のチェック＋加算（{@link #recordUsage}）は、商品在庫の原子的な増減
 * （{@link ProductService#decreaseStock}）と同じ考え方で、「在庫チェック＋更新」を1本の
 * UPDATE文で行うことで、複数ユーザーが同時に同じクーポンを使い切ろうとしても
 * 利用回数がぶれない（上限を超えて使われない）ようにしている。</p>
 */
@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /**
     * 登録済みクーポンを新しい順で全件取得する（管理画面向け）。
     *
     * @return クーポン一覧
     */
    public List<Coupon> findAll() {
        return couponRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * クーポンをIDで1件取得する。
     *
     * @param id 取得したいクーポンのID
     * @return 該当するクーポン
     * @throws IllegalArgumentException 指定したIDのクーポンが存在しない場合
     */
    public Coupon findById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("クーポンが見つかりません: " + id));
    }

    /**
     * クーポンを保存する（登録・編集共通の処理）。
     * フォームにIDが設定されていれば既存クーポンを更新し、未設定であれば新規作成する。
     * クーポンコードは重複登録を防ぐため、他クーポン（自分自身は除く）に同じコードが
     * 既に使われていないかを確認する。
     *
     * @param form 入力内容（コード・割引方式・割引額など）
     * @return 保存後のクーポンエンティティ
     * @throws IllegalArgumentException クーポンコードが他クーポンと重複している場合、
     *                                   割引方式が率なのに割引額が100を超える場合、
     *                                   または有効期間の開始日が終了日より後の場合
     */
    @Transactional
    public Coupon save(CouponForm form) {
        Coupon coupon = form.getId() != null ? findById(form.getId()) : new Coupon();
        // 入力ゆれを防ぐため、コードは前後の空白を除去し大文字に統一して保存する
        String normalizedCode = form.getCode().trim().toUpperCase();

        // 同じコードを持つ他のクーポン（自分自身の更新は除く）が既に存在しないか確認する
        couponRepository.findByCode(normalizedCode).ifPresent(existing -> {
            if (!existing.getId().equals(form.getId())) {
                throw new IllegalArgumentException("このクーポンコードは既に使用されています");
            }
        });

        // 割引方式が「率」の場合、100を超える値を許すとcalculateDiscountのint演算が桁あふれし、
        // 割引額が注文合計金額を上回る（マイナスの支払額になる）事態を招きうるため上限を設ける
        if (form.getDiscountType() == DiscountType.PERCENTAGE && form.getDiscountValue() > 100) {
            throw new IllegalArgumentException("割引方式が「率」の場合、割引額は100以下で入力してください");
        }
        // 開始日が終了日より後だと、どんな日付でも有効期間内にならず誰も使えないクーポンになってしまう
        if (form.getValidFrom() != null && form.getValidUntil() != null
                && form.getValidFrom().isAfter(form.getValidUntil())) {
            throw new IllegalArgumentException("有効期間の開始日は終了日より前の日付にしてください");
        }

        coupon.setCode(normalizedCode);
        coupon.setDiscountType(form.getDiscountType());
        coupon.setDiscountValue(form.getDiscountValue());
        coupon.setMinOrderAmount(form.getMinOrderAmount() != null ? form.getMinOrderAmount() : 0);
        coupon.setUsageLimit(form.getUsageLimit());
        coupon.setValidFrom(form.getValidFrom());
        coupon.setValidUntil(form.getValidUntil());
        coupon.setActive(form.isActive());
        return couponRepository.save(coupon);
    }

    /**
     * クーポンをIDで削除する。
     *
     * @param id 削除対象のクーポンID
     */
    public void deleteById(Long id) {
        couponRepository.deleteById(id);
    }

    /**
     * チェックアウト時にクーポンコードを検証し、適用可能であればクーポンを返す。
     * コード自体の存在確認に加え、無効化・有効期間外・最低注文金額未満のいずれかに
     * 該当する場合はエラーメッセージ付きの例外を投げる（呼び出し元でチェックアウト画面に
     * 再表示する想定。既存の在庫不足エラーと同じ IllegalStateException を使うことで、
     * OrderControllerの既存のcatchブロックにそのまま乗せられるようにしている）。
     * 利用回数上限のチェックはここでは行わず、実際に注文が確定するタイミングの
     * {@link #recordUsage} で原子的に行う（検証時点と確定時点の間の競合を避けるため）。
     *
     * @param code       入力されたクーポンコード
     * @param orderTotal 割引前の注文合計金額
     * @return 検証済みのクーポン
     * @throws IllegalStateException コードが存在しない、無効化されている、有効期間外、
     *                                または最低注文金額に満たない場合
     */
    public Coupon validate(String code, int orderTotal) {
        String normalizedCode = code.trim().toUpperCase();
        Coupon coupon = couponRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new IllegalStateException("クーポンコードが見つかりません"));

        if (!coupon.isActive()) {
            throw new IllegalStateException("このクーポンは現在利用できません");
        }
        LocalDate today = LocalDate.now();
        if (coupon.getValidFrom() != null && today.isBefore(coupon.getValidFrom())) {
            throw new IllegalStateException("このクーポンはまだ利用開始前です");
        }
        if (coupon.getValidUntil() != null && today.isAfter(coupon.getValidUntil())) {
            throw new IllegalStateException("このクーポンは有効期限が切れています");
        }
        if (orderTotal < coupon.getMinOrderAmount()) {
            throw new IllegalStateException(
                    coupon.getMinOrderAmount() + "円以上のご注文でこのクーポンを利用できます");
        }
        return coupon;
    }

    /**
     * クーポンの利用回数を原子的に1加算する。チェックアウト確定時に呼び出す。
     * {@link #validate}で有効性を確認済みであっても、確定までの間に他の注文が
     * 利用回数上限を使い切っている可能性があるため、ここでも上限チェックを行う
     * （在庫の原子的減算と同じ設計思想）。
     *
     * @param coupon 利用するクーポン
     * @throws IllegalStateException 利用回数の上限に達しており加算できなかった場合
     */
    @Transactional
    public void recordUsage(Coupon coupon) {
        int updated = couponRepository.incrementUsageIfAvailable(coupon.getId());
        if (updated == 0) {
            throw new IllegalStateException("このクーポンは利用回数の上限に達しています");
        }
    }
}
