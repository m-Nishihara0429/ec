package com.example.ec.repository;

import com.example.ec.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * FAQ(よくある質問)を管理するリポジトリ。
 */
public interface FaqRepository extends JpaRepository<Faq, Long> {

    // 公開ページ・管理画面共通で使う、表示順(displayOrder)の昇順で全件取得するクエリメソッド
    List<Faq> findAllByOrderByDisplayOrderAsc();
}
