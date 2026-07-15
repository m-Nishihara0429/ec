package com.example.ec.service;

import com.example.ec.dto.ContactForm;
import com.example.ec.entity.Inquiry;
import com.example.ec.repository.InquiryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 問い合わせ内容の登録・参照を行うサービスクラス。
 * メール送信基盤は持たないため、送信内容はDBに保存し管理画面で確認する運用を想定している。
 */
@Service
public class InquiryService {

    private final InquiryRepository inquiryRepository;

    public InquiryService(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    /**
     * 問い合わせフォームの入力内容をDBに保存する。
     *
     * @param form 送信された問い合わせ内容
     * @return 保存後の問い合わせエンティティ
     */
    public Inquiry save(ContactForm form) {
        Inquiry inquiry = new Inquiry();
        inquiry.setName(form.getName());
        inquiry.setEmail(form.getEmail());
        inquiry.setSubject(form.getSubject());
        inquiry.setMessage(form.getMessage());
        return inquiryRepository.save(inquiry);
    }

    /**
     * 新しい問い合わせから順に全件取得する（管理画面の一覧表示用）。
     *
     * @return 問い合わせの一覧（0件の場合は空リスト）
     */
    public List<Inquiry> findAll() {
        return inquiryRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * IDを指定して問い合わせを1件取得する。
     *
     * @param id 取得したい問い合わせのID
     * @return 該当する問い合わせ
     * @throws IllegalArgumentException 指定したIDの問い合わせが存在しない場合
     */
    public Inquiry findById(Long id) {
        return inquiryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("問い合わせが見つかりません: " + id));
    }

    /**
     * 指定したIDの問い合わせを削除する。
     *
     * @param id 削除対象の問い合わせID
     */
    public void deleteById(Long id) {
        inquiryRepository.deleteById(id);
    }
}
