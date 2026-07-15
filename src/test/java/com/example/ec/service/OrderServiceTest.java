package com.example.ec.service;

import com.example.ec.entity.*;
import com.example.ec.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartService cartService;

    @Mock
    private ProductService productService;

    @Mock
    private CouponService couponService;

    private OrderService orderService;

    private User user;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartService, productService, couponService);
        user = new User();
        user.setId(1L);
        user.setName("テストユーザー");
    }

    private Product product(long id, int price) {
        Product product = new Product();
        product.setId(id);
        product.setName("商品" + id);
        product.setPrice(price);
        return product;
    }

    @Test
    void checkout_カート内容から注文を作成し在庫を減らしカートを空にする() {
        Product p1 = product(10L, 1000);
        Product p2 = product(20L, 2000);
        CartItem item1 = new CartItem(user, p1, 2);
        CartItem item2 = new CartItem(user, p2, 1);
        when(cartService.findByUser(user)).thenReturn(List.of(item1, item2));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.checkout(user, "東京都渋谷区1-1-1", PaymentMethod.COD, null);

        assertThat(result.getTotalPrice()).isEqualTo(1000 * 2 + 2000 * 1);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(result.getDiscountAmount()).isZero();
        verify(productService).decreaseStock(p1, 2);
        verify(productService).decreaseStock(p2, 1);
        verify(cartService).clear(user);
    }

    @Test
    void checkout_カートが空なら例外を投げる() {
        when(cartService.findByUser(user)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.checkout(user, "住所", PaymentMethod.COD, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("カートが空");

        verify(cartService, never()).clear(any());
    }

    @Test
    void checkout_クーポン適用時は割引額が合計金額から差し引かれ利用回数が加算される() {
        Product p1 = product(10L, 1000);
        CartItem item1 = new CartItem(user, p1, 2);
        when(cartService.findByUser(user)).thenReturn(List.of(item1));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Coupon coupon = new Coupon();
        coupon.setId(5L);
        coupon.setCode("SAVE500");
        coupon.setDiscountType(DiscountType.FIXED_AMOUNT);
        coupon.setDiscountValue(500);
        when(couponService.validate("SAVE500", 2000)).thenReturn(coupon);

        Order result = orderService.checkout(user, "住所", PaymentMethod.CREDIT_CARD, "SAVE500");

        assertThat(result.getTotalPrice()).isEqualTo(1500);
        assertThat(result.getDiscountAmount()).isEqualTo(500);
        assertThat(result.getCouponCode()).isEqualTo("SAVE500");
        verify(couponService).recordUsage(coupon);
    }

    private Order orderOf(User owner, OrderStatus status, Product... products) {
        Order order = new Order();
        order.setId(99L);
        order.setUser(owner);
        order.setStatus(status);
        for (Product product : products) {
            order.addItem(new OrderItem(product, 3, product.getPrice()));
        }
        return order;
    }

    @Test
    void cancelByUser_PENDING注文は本人ならキャンセルでき在庫が戻る() {
        Product p1 = product(10L, 1000);
        Order order = orderOf(user, OrderStatus.PENDING, p1);
        when(orderRepository.findById(99L)).thenReturn(java.util.Optional.of(order));

        orderService.cancelByUser(99L, user);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productService).increaseStock(p1, 3);
        verify(orderRepository).save(order);
    }

    @Test
    void cancelByUser_他人の注文はキャンセルできない() {
        User owner = new User();
        owner.setId(2L);
        Order order = orderOf(owner, OrderStatus.PENDING, product(10L, 1000));
        when(orderRepository.findById(99L)).thenReturn(java.util.Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelByUser(99L, user))
                .isInstanceOf(IllegalArgumentException.class);
        verify(productService, never()).increaseStock(any(), anyInt());
    }

    @Test
    void cancelByUser_発送済みはキャンセルできない() {
        Order order = orderOf(user, OrderStatus.SHIPPED, product(10L, 1000));
        when(orderRepository.findById(99L)).thenReturn(java.util.Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelByUser(99L, user))
                .isInstanceOf(IllegalStateException.class);
        verify(productService, never()).increaseStock(any(), anyInt());
    }

    @Test
    void cancelByAdmin_発送済みでもキャンセルでき在庫が戻る() {
        Product p1 = product(10L, 1000);
        Order order = orderOf(user, OrderStatus.SHIPPED, p1);
        when(orderRepository.findById(99L)).thenReturn(java.util.Optional.of(order));

        orderService.cancelByAdmin(99L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productService).increaseStock(p1, 3);
    }

    @Test
    void cancelByAdmin_完了済みはキャンセルできない() {
        Order order = orderOf(user, OrderStatus.COMPLETED, product(10L, 1000));
        when(orderRepository.findById(99L)).thenReturn(java.util.Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelByAdmin(99L))
                .isInstanceOf(IllegalStateException.class);
        verify(productService, never()).increaseStock(any(), anyInt());
    }

    @Test
    void cancelByAdmin_既にキャンセル済みは再キャンセルできない() {
        Order order = orderOf(user, OrderStatus.CANCELLED, product(10L, 1000));
        when(orderRepository.findById(99L)).thenReturn(java.util.Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelByAdmin(99L))
                .isInstanceOf(IllegalStateException.class);
        verify(productService, never()).increaseStock(any(), anyInt());
    }
}
