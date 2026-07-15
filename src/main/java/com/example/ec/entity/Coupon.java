package com.example.ec.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * クーポン（割引）エンティティ。チェックアウト時にコードを入力すると、
 * 条件（有効期間・最低注文金額・利用回数上限）を満たしていれば注文合計金額から割引される。
 * DB上のテーブル名は "coupons"。
 */
@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // クーポンID（主キー）

    @Column(nullable = false, unique = true, length = 30)
    private String code; // 注文時に入力するクーポンコード（重複不可）

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType discountType; // 割引方式（率 or 固定額）

    @Column(nullable = false)
    private Integer discountValue; // 割引率(1-100)または割引額(円)。discountTypeに応じて解釈が変わる

    // 既存データ（このクーポン導入前のクーポンは存在しないため実質新規のみだが、
    // NOT NULL列をddl-auto=updateでSQLiteに追加する際はDEFAULT句が無いと失敗するため
    // ColumnDefaultを付けている（Userエンティティのenabled列と同じ対策）
    @ColumnDefault("0")
    @Column(nullable = false)
    private Integer minOrderAmount = 0; // このクーポンを適用できる最低注文金額（0なら制限なし）

    private Integer usageLimit; // 総利用可能回数の上限（nullなら無制限）

    @ColumnDefault("0")
    @Column(nullable = false)
    private Integer usedCount = 0; // これまでに利用された回数

    private LocalDate validFrom; // 有効期間の開始日（nullなら開始日の制限なし）

    private LocalDate validUntil; // 有効期間の終了日（nullなら終了日の制限なし）

    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean active = true; // 無効化フラグ。falseにすると即座に利用不可にできる

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 指定した注文合計金額に対する割引額を計算する。
     * 固定額クーポンで注文合計金額を上回る割引額が設定されていても、
     * 割引後の金額がマイナスにならないよう注文合計金額を上限とする。
     *
     * @param orderTotal 割引前の注文合計金額
     * @return 割引額（0以上、orderTotal以下）
     */
    public int calculateDiscount(int orderTotal) {
        int discount = discountType == DiscountType.PERCENTAGE
                ? orderTotal * discountValue / 100
                : discountValue;
        return Math.min(discount, orderTotal);
    }
}
