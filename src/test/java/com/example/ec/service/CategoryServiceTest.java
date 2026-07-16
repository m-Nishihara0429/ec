package com.example.ec.service;

import com.example.ec.entity.Category;
import com.example.ec.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
    }

    @Test
    void save_前後の空白を除去して保存する() {
        when(categoryRepository.existsByName("食品")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category saved = categoryService.save(new Category("  食品  "));

        assertThat(saved.getName()).isEqualTo("食品");
    }

    @Test
    void save_空白のみの名前は拒否される() {
        assertThatThrownBy(() -> categoryService.save(new Category("   ")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void save_同名カテゴリが既に存在する場合は拒否される() {
        when(categoryRepository.existsByName("食品")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.save(new Category("食品")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void save_100文字を超える名前は拒否される() {
        String tooLong = "あ".repeat(101);

        assertThatThrownBy(() -> categoryService.save(new Category(tooLong)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(categoryRepository, never()).save(any());
    }
}
