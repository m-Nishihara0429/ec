package com.example.ec.specification;

import com.example.ec.constant.SearchSynonyms;
import com.example.ec.entity.Product;
import com.example.ec.util.TextNormalizer;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 商品検索用のJPA Specificationを組み立てるユーティリティ。
 * キーワード検索では、TextNormalizerとSearchSynonymsを使って
 * 表記ゆれ・同義語を含む複数の検索語バリエーションを作り、それらをOR条件で
 * 商品名/説明文に一致させることで、「へっどほん」で「ワイヤレスイヤホン」がヒットするような
 * 語彙の異なる検索を実現している。
 * Specificationは Spring Data JPA が提供する「動的にWHERE句を組み立てる」ための仕組みで、
 * ここで作った各SpecificationをリポジトリのfindAll(Specification)などに渡すことで
 * 条件を組み合わせた検索SQLが生成される。
 */
public final class ProductSpecifications {

    /** インスタンス化を禁止するためのprivateコンストラクタ（staticメソッドのみを提供するユーティリティクラス）。 */
    private ProductSpecifications() {
    }

    /**
     * カテゴリIDが一致する商品だけに絞り込むSpecification（WHERE category_id = :categoryId 相当）。
     * @param categoryId 絞り込み対象のカテゴリID（nullの場合は絞り込み条件なし）
     * @return categoryIdによる絞り込み条件。categoryIdがnullならば条件を付与しない(null)
     */
    public static Specification<Product> categoryEquals(Long categoryId) {
        // categoryIdがnullなら条件なし(null)を返し、Specification全体としてこの条件を無視させる。
        // そうでなければ、root(Product)のcategory.idがcategoryIdと等しいという条件(cb.equal)を返す
        return (root, query, cb) -> categoryId == null ? null : cb.equal(root.get("category").get("id"), categoryId);
    }

    /** キーワードのバリエーション(表記ゆれ・同義語)ごとにLIKE条件を作り、name/descriptionへOR結合する。 */
    public static Specification<Product> keywordContains(String keyword) {
        // ラムダ式でSpecification（root=検索対象エンティティ, query=クエリ全体, cb=条件組み立てビルダー）を返す
        return (root, query, cb) -> {
            // キーワードが未入力（null・空白のみ）なら絞り込み条件なしとする
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            // OR条件としてまとめるPredicate（条件式）の一覧
            List<Predicate> predicates = new ArrayList<>();
            // 前後の空白を除去したキーワードから、表記ゆれ・同義語を考慮した検索語バリエーションを生成し、1つずつ処理する
            for (String variant : searchVariants(keyword.trim())) {
                // LIKE検索用に、前後にワイルドカード(%)を付けた小文字パターンを作る（部分一致・大小文字無視のため）
                String pattern = "%" + variant.toLowerCase() + "%";
                // 商品名(name)を小文字化してpatternに部分一致するかどうかの条件を追加（WHERE LOWER(name) LIKE '%variant%' 相当）
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
                // 商品説明(description)を小文字化してpatternに部分一致するかどうかの条件を追加（WHERE LOWER(description) LIKE '%variant%' 相当）
                predicates.add(cb.like(cb.lower(root.get("description")), pattern));
            }
            // すべての条件をOR結合して1つのPredicateにまとめて返す（いずれかの条件に一致すればヒット）
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    /** 全角/半角・ひらがな/カタカナの表記ゆれと同義語辞書を吸収した検索語のバリエーションを作る。 */
    private static Set<String> searchVariants(String rawKeyword) {
        // まず全角/半角の表記ゆれをNFKC正規化で統一する
        String normalized = TextNormalizer.normalizeWidth(rawKeyword);
        // 正規化した文字列をひらがなに変換したバージョンを作る
        String hiragana = TextNormalizer.toHiragana(normalized);
        // 正規化した文字列をカタカナに変換したバージョンを作る
        String katakana = TextNormalizer.toKatakana(normalized);

        // 正規化版・ひらがな版・カタカナ版をまとめる（挿入順を保持するLinkedHashSetで重複除去）
        Set<String> variants = new LinkedHashSet<>(List.of(normalized, hiragana, katakana));
        // 上記のバリエーションに加えて、同義語展開した語も追加していくための集合（元のvariantsをコピーして初期化）
        Set<String> expanded = new LinkedHashSet<>(variants);
        // 各バリエーションについて、同義語辞書(SearchSynonyms)を引いて関連語をすべて追加する
        for (String variant : variants) {
            expanded.addAll(SearchSynonyms.expand(variant));
        }
        // 表記ゆれ・同義語をすべて含んだ検索語バリエーション集合を返す
        return expanded;
    }

    /**
     * 価格が指定値以上の商品に絞り込むSpecification（WHERE price >= :minPrice 相当）。
     * @param minPrice 最低価格（nullの場合は絞り込み条件なし）
     * @return minPriceによる絞り込み条件
     */
    public static Specification<Product> priceGreaterThanOrEqual(Integer minPrice) {
        // minPriceがnullなら条件なし、そうでなければpriceがminPrice以上という条件を返す
        return (root, query, cb) -> minPrice == null ? null : cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    /**
     * 価格が指定値以下の商品に絞り込むSpecification（WHERE price <= :maxPrice 相当）。
     * @param maxPrice 最高価格（nullの場合は絞り込み条件なし）
     * @return maxPriceによる絞り込み条件
     */
    public static Specification<Product> priceLessThanOrEqual(Integer maxPrice) {
        // maxPriceがnullなら条件なし、そうでなければpriceがmaxPrice以下という条件を返す
        return (root, query, cb) -> maxPrice == null ? null : cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }
}
