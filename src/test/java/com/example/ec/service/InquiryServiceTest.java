package com.example.ec.service;

import com.example.ec.dto.ContactForm;
import com.example.ec.entity.Inquiry;
import com.example.ec.repository.InquiryRepository;
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
class InquiryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    private InquiryService inquiryService;

    @BeforeEach
    void setUp() {
        inquiryService = new InquiryService(inquiryRepository);
    }

    @Test
    void save_フォームの入力内容がそのまま問い合わせエンティティに反映される() {
        ContactForm form = new ContactForm();
        form.setName("テスト太郎");
        form.setEmail("test@example.com");
        form.setSubject("動作について");
        form.setMessage("問い合わせ本文です");
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(inv -> inv.getArgument(0));

        inquiryService.save(form);

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryRepository).save(captor.capture());
        Inquiry saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("テスト太郎");
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getSubject()).isEqualTo("動作について");
        assertThat(saved.getMessage()).isEqualTo("問い合わせ本文です");
    }

    @Test
    void findById_存在しないIDは例外になる() {
        when(inquiryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findById_存在するIDはそのまま返る() {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(1L);
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));

        assertThat(inquiryService.findById(1L)).isEqualTo(inquiry);
    }
}
