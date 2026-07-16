package com.example.ec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理画面のFAQ登録・編集フォームの入力値を受け取るDTO。
 * idがnullなら新規登録、設定済みなら更新として扱う。
 */
@Getter
@Setter
public class FaqForm {

    private Long id;

    // @Size はエンティティ側の列長（Faq.question, length=200）に合わせる
    @NotBlank(message = "質問を入力してください")
    @Size(max = 200, message = "質問は200文字以内で入力してください")
    private String question;

    @NotBlank(message = "回答を入力してください")
    private String answer;

    // 表示順。未入力の場合はサービス層で0として扱う
    private Integer displayOrder;
}
