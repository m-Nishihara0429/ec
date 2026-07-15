package com.example.ec.service;

import com.example.ec.dto.FaqForm;
import com.example.ec.entity.Faq;
import com.example.ec.repository.FaqRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FAQ(よくある質問)の参照・登録・更新・削除を行うサービスクラス。
 */
@Service
public class FaqService {

    private final FaqRepository faqRepository;

    public FaqService(FaqRepository faqRepository) {
        this.faqRepository = faqRepository;
    }

    /**
     * 表示順(displayOrder)の昇順で全FAQを取得する。公開ページ・管理画面共通で使用する。
     *
     * @return FAQの一覧（0件の場合は空リスト）
     */
    public List<Faq> findAll() {
        return faqRepository.findAllByOrderByDisplayOrderAsc();
    }

    /**
     * IDを指定してFAQを1件取得する。
     *
     * @param id 取得したいFAQのID
     * @return 該当するFAQ
     * @throws IllegalArgumentException 指定したIDのFAQが存在しない場合
     */
    public Faq findById(Long id) {
        return faqRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("FAQが見つかりません: " + id));
    }

    /**
     * FAQを保存する（登録・編集共通の処理）。
     * フォームにIDが設定されていれば既存FAQを検索して更新し、未設定であれば新規作成する。
     *
     * @param form 入力内容（質問・回答・表示順）
     * @return 保存後のFAQエンティティ
     */
    public Faq save(FaqForm form) {
        Faq faq = form.getId() != null ? findById(form.getId()) : new Faq();
        faq.setQuestion(form.getQuestion());
        faq.setAnswer(form.getAnswer());
        // 表示順が未入力の場合は0を既定値として扱う
        faq.setDisplayOrder(form.getDisplayOrder() != null ? form.getDisplayOrder() : 0);
        return faqRepository.save(faq);
    }

    /**
     * 指定したIDのFAQを削除する。
     *
     * @param id 削除対象のFAQ ID
     */
    public void deleteById(Long id) {
        faqRepository.deleteById(id);
    }
}
