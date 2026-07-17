package com.example.ec.service;

import com.example.ec.dto.FaqForm;
import com.example.ec.entity.Faq;
import com.example.ec.repository.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock
    private FaqRepository faqRepository;

    private FaqService faqService;

    @BeforeEach
    void setUp() {
        faqService = new FaqService(faqRepository);
    }

    @Test
    void findById_存在しないIDは例外になる() {
        when(faqRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faqService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void save_IDが無ければ新規作成される() {
        FaqForm form = new FaqForm();
        form.setQuestion("質問");
        form.setAnswer("回答");
        form.setDisplayOrder(3);
        when(faqRepository.save(any(Faq.class))).thenAnswer(inv -> inv.getArgument(0));

        Faq saved = faqService.save(form);

        assertThat(saved.getQuestion()).isEqualTo("質問");
        assertThat(saved.getAnswer()).isEqualTo("回答");
        assertThat(saved.getDisplayOrder()).isEqualTo(3);
    }

    @Test
    void save_表示順が未入力の場合は0として保存される() {
        FaqForm form = new FaqForm();
        form.setQuestion("質問");
        form.setAnswer("回答");
        form.setDisplayOrder(null);
        when(faqRepository.save(any(Faq.class))).thenAnswer(inv -> inv.getArgument(0));

        Faq saved = faqService.save(form);

        assertThat(saved.getDisplayOrder()).isEqualTo(0);
    }

    @Test
    void save_IDがあれば既存FAQを更新する() {
        Faq existing = new Faq();
        existing.setId(5L);
        existing.setQuestion("旧質問");
        existing.setAnswer("旧回答");
        existing.setDisplayOrder(1);
        when(faqRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(faqRepository.save(any(Faq.class))).thenAnswer(inv -> inv.getArgument(0));

        FaqForm form = new FaqForm();
        form.setId(5L);
        form.setQuestion("新質問");
        form.setAnswer("新回答");
        form.setDisplayOrder(2);

        Faq saved = faqService.save(form);

        ArgumentCaptor<Faq> captor = ArgumentCaptor.forClass(Faq.class);
        verify(faqRepository).save(captor.capture());
        // 新規レコードではなく同一ID（同一レコード）が更新されること
        assertThat(captor.getValue().getId()).isEqualTo(5L);
        assertThat(saved.getQuestion()).isEqualTo("新質問");
        assertThat(saved.getAnswer()).isEqualTo("新回答");
        assertThat(saved.getDisplayOrder()).isEqualTo(2);
    }
}
