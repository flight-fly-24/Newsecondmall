package com.example.mall.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductRelationService {
    private final JdbcClient jdbc;
    private final ProductService products;

    public ProductRelationService(JdbcClient jdbc, ProductService products) {
        this.jdbc = jdbc;
        this.products = products;
    }

    public List<RelationItem> list(String type, long userId) {
        return switch (normalize(type)) {
            case "favorite" -> jdbc.sql("""
                    SELECT f.id, COALESCE(g.name, f.item_title) AS title, 'favorite' AS type,
                           f.created_at, f.goods_id AS target_id
                    FROM favorite_goods f LEFT JOIN goods g ON g.goods_id=f.goods_id
                    WHERE f.user_id=:userId ORDER BY f.created_at DESC, f.id DESC
                    """).param("userId", userId).query(RelationItem.class).list();
            case "history" -> jdbc.sql("""
                    SELECT h.id, COALESCE(g.name, h.item_title) AS title, 'history' AS type,
                           h.viewed_at AS created_at, h.goods_id AS target_id
                    FROM browse_history h LEFT JOIN goods g ON g.goods_id=h.goods_id
                    WHERE h.user_id=:userId ORDER BY h.viewed_at DESC, h.id DESC
                    """).param("userId", userId).query(RelationItem.class).list();
            default -> throw unsupportedType();
        };
    }

    @Transactional
    public RelationItem add(String type, long userId, long goodsId) {
        ProductService.ProductView product = products.find(goodsId);
        switch (normalize(type)) {
            case "favorite" -> {
                int existing = jdbc.sql("SELECT COUNT(*) FROM favorite_goods WHERE user_id=:userId AND goods_id=:goodsId")
                        .param("userId", userId).param("goodsId", goodsId).query(Integer.class).single();
                if (existing == 0) {
                    jdbc.sql("INSERT INTO favorite_goods(user_id, goods_id, item_title) VALUES (:userId, :goodsId, :title)")
                            .param("userId", userId).param("goodsId", goodsId).param("title", product.name()).update();
                }
            }
            case "history" -> jdbc.sql("INSERT INTO browse_history(user_id, goods_id, item_title) VALUES (:userId, :goodsId, :title)")
                    .param("userId", userId).param("goodsId", goodsId).param("title", product.name()).update();
            default -> throw unsupportedType();
        }
        return list(type, userId).stream().filter(item -> item.targetId() == goodsId).findFirst().orElseThrow();
    }

    public void clear(String type, long userId) {
        switch (normalize(type)) {
            case "favorite" -> jdbc.sql("DELETE FROM favorite_goods WHERE user_id=:userId").param("userId", userId).update();
            case "history" -> jdbc.sql("DELETE FROM browse_history WHERE user_id=:userId").param("userId", userId).update();
            default -> throw unsupportedType();
        }
    }

    private String normalize(String type) { return type == null ? "" : type.trim().toLowerCase(); }

    private ResponseStatusException unsupportedType() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "only favorite and history are supported by catalog-service");
    }

    public record RelationItem(long id, String title, String type, LocalDateTime createdAt, long targetId) {}
}
