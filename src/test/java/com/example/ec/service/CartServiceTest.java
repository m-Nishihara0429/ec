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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
    void addToCart_数量0以下は拒否され原子的更新は呼ばれない() {
        assertThatThrownBy(() -> cartService.addToCart(user, product, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cartService.addToCart(user, product, -1))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cartItemRepository, never()).incrementQuantityIfWithinLimit(any(), any(), anyInt(), anyInt());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addToCart_単独の数量自体が上限を超える場合は拒否され原子的更新は呼ばれない() {
        assertThatThrownBy(() -> cartService.addToCart(user, product, 100))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cartItemRepository, never()).incrementQuantityIfWithinLimit(any(), any(), anyInt(), anyInt());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addToCart_既存行があれば原子的UPDATEのみで加算されfindByUserAndProductやsaveは呼ばれない() {
        // incrementQuantityIfWithinLimitが1件更新（成功）を返すケース。
        // 「読み取り→加算→保存」を経由せず、この1本のUPDATEだけで完結することを確認する
        // （lost updateを防ぐための原子的更新の核心部分）。
        when(cartItemRepository.incrementQuantityIfWithinLimit(user, product, 3, 99)).thenReturn(1);

        cartService.addToCart(user, product, 3);

        verify(cartItemRepository, never()).findByUserAndProduct(any(), any());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addToCart_既存行が無い場合は原子的UPDATEが0件でも新規行としてINSERTされる() {
        when(cartItemRepository.incrementQuantityIfWithinLimit(user, product, 3, 99)).thenReturn(0);
        when(cartItemRepository.findByUserAndProduct(user, product)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.addToCart(user, product, 3);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(3);
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getProduct()).isEqualTo(product);
    }

    @Test
    void addToCart_既存行はあるが加算後に上限を超える場合は拒否されsaveは呼ばれない() {
        // incrementQuantityIfWithinLimitが0件（上限超過でUPDATE条件に合致しなかった）を返し、
        // かつfindByUserAndProductで既存行の存在が確認できるケース。
        // 「新規追加（行が無い）」ではなく「上限超過」であると正しく切り分けられることを確認する。
        CartItem existing = new CartItem(user, product, 98);
        when(cartItemRepository.incrementQuantityIfWithinLimit(user, product, 5, 99)).thenReturn(0);
        when(cartItemRepository.findByUserAndProduct(user, product)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cartService.addToCart(user, product, 5))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addToCart_上限ちょうどは原子的UPDATEで許可される() {
        when(cartItemRepository.incrementQuantityIfWithinLimit(eq(user), eq(product), eq(9), eq(99))).thenReturn(1);

        cartService.addToCart(user, product, 9);

        verify(cartItemRepository).incrementQuantityIfWithinLimit(user, product, 9, 99);
        verify(cartItemRepository, never()).save(any());
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
