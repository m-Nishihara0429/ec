package com.example.ec.service;

import com.example.ec.entity.CartItem;
import com.example.ec.entity.Product;
import com.example.ec.entity.User;
import com.example.ec.repository.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ショッピングカートの商品追加・数量変更・削除・合計金額計算を行うサービスクラス。
 * カートの中身（CartItem）はユーザーごとに管理され、注文（OrderService.checkout）で
 * 実際の注文に変換されるまでの「一時的な買い物かご」の役割を持つ。
 */
@Service // このクラスをSpringのサービス層Beanとして登録する
public class CartService {

    // カート明細（CartItem）のデータアクセスを担当するリポジトリ
    private final CartItemRepository cartItemRepository;

    // コンストラクタインジェクションでリポジトリを受け取る
    public CartService(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    /**
     * 指定したユーザーのカート内商品一覧を取得する。
     *
     * @param user 対象ユーザー
     * @return そのユーザーのカート明細一覧（空の場合は空リスト）
     */
    public List<CartItem> findByUser(User user) {
        // ユーザーに紐づくカート明細をリポジトリから取得してそのまま返す
        return cartItemRepository.findByUser(user);
    }

    /**
     * カートに商品を追加する。
     * 既に同じ商品がカートにある場合は数量を加算し、なければ新規にカート明細を作成する。
     *
     * @param user     カートの持ち主
     * @param product  追加する商品
     * @param quantity 追加する数量
     */
    // 既に同じ商品がカートにある場合は数量を加算し、なければ新規にカート明細を作成する
    @Transactional // 検索と保存を1つのトランザクションにまとめ、途中で失敗した場合に中途半端な状態が残らないようにする
    public void addToCart(User user, Product product, int quantity) {
        // ユーザー×商品の組み合わせで既存のカート明細を検索し、無ければ数量0の新規明細を作る
        CartItem item = cartItemRepository.findByUserAndProduct(user, product)
                .orElseGet(() -> new CartItem(user, product, 0));
        // 既存数量に今回追加する数量を足し込む（新規の場合は0+quantity=quantityになる）
        item.setQuantity(item.getQuantity() + quantity);
        // 変更後の明細を保存する（IDが無ければINSERT、あればUPDATEされる）
        cartItemRepository.save(item);
    }

    /**
     * 指定したカート明細の数量を、加算ではなく指定値そのものに置き換える。
     *
     * @param cartItemId 更新対象のカート明細ID
     * @param quantity   設定したい数量
     * @throws IllegalArgumentException 指定したIDのカート明細が存在しない場合
     */
    @Transactional // 検索と更新をまとめて1トランザクションにする
    public void updateQuantity(Long cartItemId, int quantity) {
        // IDでカート明細を検索し、存在しなければ例外を投げる
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("カート商品が見つかりません: " + cartItemId));
        // 数量を指定された値に置き換える（addToCartと違い加算ではなく上書き）
        item.setQuantity(quantity);
        // 変更を保存する
        cartItemRepository.save(item);
    }

    /**
     * カート明細を1件削除する。
     *
     * @param cartItemId 削除対象のカート明細ID
     */
    @Transactional // 削除処理を明示的にトランザクション化する
    public void removeItem(Long cartItemId) {
        // 指定IDのカート明細を削除する
        cartItemRepository.deleteById(cartItemId);
    }

    /**
     * 指定ユーザーのカートを空にする。注文（checkout）完了後の後始末として使われる。
     *
     * @param user カートを空にする対象ユーザー
     */
    @Transactional // ユーザーの全カート明細をまとめて削除するのでトランザクション化する
    public void clear(User user) {
        // そのユーザーに紐づくカート明細を一括削除する
        cartItemRepository.deleteByUser(user);
    }

    /**
     * カート内商品の合計金額を計算する。
     *
     * @param user 対象ユーザー
     * @return カート内全商品の小計（単価×数量）を合計した金額
     */
    public int totalPrice(User user) {
        // ユーザーのカート明細を取得し、各明細の小計（subtotal＝単価×数量）を合算する
        return findByUser(user).stream().mapToInt(CartItem::subtotal).sum();
    }
}
