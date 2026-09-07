package com.example.mall.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StoreService {
    private static final String STORE_COLUMNS = """
            SELECT s.store_id, s.seller_id, s.seller_name, s.store_name, s.score,
                   s.credit_score, s.store_desc, s.badge, s.service_tags,
                   (SELECT COUNT(*) FROM follow_store f WHERE f.store_id=s.store_id) AS follower_count,
                   COUNT(g.goods_id) AS product_count,
                   COALESCE(SUM(CASE WHEN g.scene='new' THEN 1 ELSE 0 END), 0) AS new_count,
                   COALESCE(SUM(CASE WHEN g.scene='used' THEN 1 ELSE 0 END), 0) AS used_count,
                   FALSE AS followed
            FROM store s LEFT JOIN goods g ON g.store_id=s.store_id AND g.status IN ('ON_SALE', 'approved', '0')
            """;
    private static final String GROUPING = " GROUP BY s.store_id, s.seller_id, s.seller_name, s.store_name, s.score, s.credit_score, s.store_desc, s.badge, s.service_tags";
    private final JdbcClient jdbc;

    public StoreService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<StoreView> list() {
        return jdbc.sql(STORE_COLUMNS + " WHERE s.status='normal'" + GROUPING + " ORDER BY s.store_id")
                .query(StoreView.class).list();
    }

    public StoreView find(long id) {
        return jdbc.sql(STORE_COLUMNS + " WHERE s.store_id=:id AND s.status='normal'" + GROUPING)
                .param("id", id).query(StoreView.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "store not found"));
    }

    public StoreView mine(long sellerId) {
        return jdbc.sql(STORE_COLUMNS + " WHERE s.seller_id=:sellerId AND s.status='normal'" + GROUPING + " ORDER BY s.store_id LIMIT 1")
                .param("sellerId", sellerId).query(StoreView.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "store not found"));
    }

    public List<ProductService.ProductView> products(long storeId) {
        find(storeId);
        return jdbc.sql(ProductService.PRODUCT_COLUMNS + " WHERE store_id=:storeId AND status IN ('ON_SALE', 'approved', '0') ORDER BY created_at DESC, goods_id DESC")
                .param("storeId", storeId).query(ProductService.ProductView.class).list();
    }

    public StoreView find(long id, long userId) {
        boolean followed = jdbc.sql("SELECT COUNT(*) FROM follow_store WHERE user_id=:userId AND store_id=:storeId")
                .param("userId", userId).param("storeId", id).query(Integer.class).single() > 0;
        return withFollowed(find(id), followed);
    }

    public StoreView follow(long id, long userId) {
        StoreView store = find(id);
        if (store.sellerId() == userId) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能关注自己的店铺");
        jdbc.sql("INSERT IGNORE INTO follow_store(user_id, store_id, store_name) VALUES (:userId, :storeId, :name)")
                .param("userId", userId).param("storeId", id).param("name", store.storeName()).update();
        return withFollowed(find(id), true);
    }

    public StoreView unfollow(long id, long userId) {
        jdbc.sql("DELETE FROM follow_store WHERE user_id=:userId AND store_id=:storeId")
                .param("userId", userId).param("storeId", id).update();
        return withFollowed(find(id), false);
    }

    private StoreView withFollowed(StoreView store, boolean followed) {
        return new StoreView(store.storeId(), store.sellerId(), store.sellerName(), store.storeName(), store.score(),
                store.creditScore(), store.storeDesc(), store.badge(), store.serviceTags(), store.followerCount(),
                store.productCount(), store.newCount(), store.usedCount(), followed);
    }

    public record StoreView(long storeId, long sellerId, String sellerName, String storeName,
                            BigDecimal score, int creditScore, String storeDesc, String badge,
                            String serviceTags, long followerCount, long productCount, long newCount, long usedCount,
                            boolean followed) {
        public String id() { return String.valueOf(storeId); }
        public String name() { return storeName; }
        public String desc() { return storeDesc; }
        public String fans() { return String.valueOf(followerCount); }
    }
}
