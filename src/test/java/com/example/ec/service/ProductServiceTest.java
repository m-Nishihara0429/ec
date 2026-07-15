package com.example.ec.service;

import com.example.ec.entity.Product;
import com.example.ec.repository.CategoryRepository;
import com.example.ec.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ReviewService reviewService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository, reviewService);
    }

    @Test
    void decreaseStock_在庫が十分なら減算する() {
        Product product = new Product();
        product.setId(1L);
        product.setName("テスト商品");
        when(productRepository.decreaseStockIfAvailable(1L, 3)).thenReturn(1);

        productService.decreaseStock(product, 3);

        verify(productRepository).decreaseStockIfAvailable(1L, 3);
    }

    @Test
    void decreaseStock_在庫不足なら例外を投げる() {
        Product product = new Product();
        product.setId(1L);
        product.setName("テスト商品");
        when(productRepository.decreaseStockIfAvailable(1L, 100)).thenReturn(0);

        assertThatThrownBy(() -> productService.decreaseStock(product, 100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("テスト商品");
    }

    @Test
    void increaseStock_原子的な加算クエリを呼ぶ() {
        Product product = new Product();
        product.setId(2L);

        productService.increaseStock(product, 5);

        verify(productRepository).increaseStockAtomic(2L, 5);
    }
}
