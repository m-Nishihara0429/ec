package com.example.ec.service;

import com.example.ec.entity.CartItem;
import com.example.ec.entity.Product;
import com.example.ec.entity.User;
import com.example.ec.repository.CartItemRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    private CartService cartService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartItemRepository);
        user = new User();
        user.setId(1L);
        product = new Product();
        product.setId(10L);
        product.setName("テスト商品");
        product.setPrice(1000);
    }

    @Test
    void addToCart_新規追加なら指定数量で保存される() {
        when(cartItemRepository.findByUserAndProduct(user, product)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.addToCart(user, product, 3);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(3);
    }

    @Test
    void addToCart_数量0以下は拒否され保存されない() {
        assertThatThrownBy(() -> cartService.addToCart(user, product, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cartService.addToCart(user, product, -1))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addToCart_加算後の数量が上限を超える場合は拒否される() {
        CartItem existing = new CartItem(user, product, 98);
        when(cartItemRepository.findByUserAndProduct(user, product)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cartService.addToCart(user, product, 5))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addToCart_上限ちょうどは許可される() {
        CartItem existing = new CartItem(user, product, 90);
        when(cartItemRepository.findByUserAndProduct(user, product)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.addToCart(user, product, 9);

        assertThat(existing.getQuantity()).isEqualTo(99);
    }

    @Test
    void updateQuantity_範囲外の数量は拒否される() {
        assertThatThrownBy(() -> cartService.updateQuantity(1L, 0, user))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cartService.updateQuantity(1L, 100, user))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cartItemRepository, never()).findById(any());
    }
}
