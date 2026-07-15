package com.example.ec.repository;

import com.example.ec.entity.Order;
import com.example.ec.entity.OrderStatus;
import com.example.ec.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 注文(Order)情報を管理するリポジトリ。
 * ユーザーの注文履歴表示や、管理画面の売上集計・ダッシュボード表示に利用する。
 * JpaRepository<Order, Long> を継承することで、
 * save・findById・findAll・deleteなどの基本的なCRUD操作が自動的に使えるようになる。
 * Order はエンティティ型、Long は主キー(id)の型を表す。
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
    // 「userカラムが一致するOrderを全件検索し、createdAt(注文日時)の降順(新しい順)に並べる」
    // クエリメソッド。マイページの「注文履歴」一覧表示に使用する。
    List<Order> findByUserOrderByCreatedAtDesc(User user);

    // 「全てのOrderを、createdAt(注文日時)の降順(新しい順)に並べて取得する」クエリメソッド。
    // 管理画面の注文一覧ページに全ユーザー分の注文を新着順で表示するために使用する。
    List<Order> findAllByOrderByCreatedAtDesc();

    // 管理画面ダッシュボードに表示する「直近の注文」上位5件を取得する
    // 「createdAt(注文日時)の降順に並べ、先頭5件のみ取得する」クエリメソッド(Top5)。
    List<Order> findTop5ByOrderByCreatedAtDesc();

    // 「statusカラムが指定した値と一致するOrderの件数」を数えるクエリメソッド。
    // 管理画面ダッシュボードで「処理待ち件数」など、状態別の注文数を表示するために使用する。
    long countByStatus(OrderStatus status);

    // 売上集計用。指定ステータス(例: キャンセル済み)の注文を除外して合計金額を求める。
    // 対象注文が0件でもCOALESCEによりnullではなく0を返す。
    // @Query によりJPQLを直接記述している。SUM(o.totalPrice) で合計金額を計算し、
    // WHERE句で excludedStatus と異なる(<>)注文だけを集計対象とすることで、
    // キャンセル済み注文などを売上から除外している。COALESCE(値, 0) は
    // 集計対象が0件でSUMがnullになる場合に代わりに0を返す関数。
    // @Param("excludedStatus") によりメソッド引数を JPQL内の :excludedStatus に束縛する。
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status <> :excludedStatus")
    long sumTotalPriceExcludingStatus(@Param("excludedStatus") OrderStatus excludedStatus);

    // 日別売上グラフ用。指定日時以降・指定ステータス（キャンセル）を除いた注文の(注文日時, 合計金額)の組を
    // 注文日時の昇順で取得する。日単位への丸め込みと合算はサービス層（OrderService）でJava側で行う
    // （SQLite依存の日付関数を使わず、JPQLの範囲で完結させるための設計）。
    @Query("SELECT o.createdAt, o.totalPrice FROM Order o " +
            "WHERE o.status <> :excludedStatus AND o.createdAt >= :from " +
            "ORDER BY o.createdAt ASC")
    List<Object[]> findCreatedAtAndTotalPriceSince(@Param("excludedStatus") OrderStatus excludedStatus,
                                                     @Param("from") LocalDateTime from);

    // カテゴリ別売上グラフ用。指定日時以降・指定ステータス（キャンセル）を除いた注文に含まれる
    // 明細（OrderItem）を、商品が属するカテゴリ名でグルーピングし、小計（単価×数量）の合計金額を求める。
    // 日別売上グラフと期間（直近14日間／12週間／12ヶ月）を揃えられるよう、fromで開始日時を絞り込む。
    // 商品にカテゴリが設定されていない場合はLEFT JOINでc（category）がnullになるため、
    // COALESCEで「未分類」というラベルに読み替える。
    @Query("SELECT COALESCE(c.name, '未分類'), SUM(oi.price * oi.quantity) " +
            "FROM OrderItem oi JOIN oi.product p LEFT JOIN p.category c " +
            "WHERE oi.order.status <> :excludedStatus AND oi.order.createdAt >= :from " +
            "GROUP BY c.name " +
            "ORDER BY SUM(oi.price * oi.quantity) DESC")
    List<Object[]> sumSalesByCategorySince(@Param("excludedStatus") OrderStatus excludedStatus,
                                            @Param("from") LocalDateTime from);
}
