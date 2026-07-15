package com.example.ec.repository;

import com.example.ec.entity.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 問い合わせ内容を管理するリポジトリ。
 */
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    // 管理画面の一覧表示用。新しい問い合わせから順に全件取得するクエリメソッド
    List<Inquiry> findAllByOrderByCreatedAtDesc();
}
