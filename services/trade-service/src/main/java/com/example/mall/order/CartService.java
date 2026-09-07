package com.example.mall.order;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {
    private final JdbcClient jdbc;
    private final CatalogClient catalog;
    private final UserClient users;

    public CartService(JdbcClient jdbc, CatalogClient catalog, UserClient users) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.users = users;
    }

    public List<CartItemView> list(String authorization) {
        long userId = users.requireLogin(authorization).userId();
        return rows(userId).stream().map(this::toView).toList();
    }

    public List<CartItemView> add(String authorization, AddCartRequest request) {
        long userId = users.requireLogin(authorization).userId();
        long goodsId = request == null || request.goodsId() == null ? 0 : request.goodsId();
        if (goodsId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品信息缺失");
        }
        CatalogClient.ProductSnapshot product = requireProduct(goodsId);
        if (!"ON_SALE".equals(product.status()) && !"approved".equalsIgnoreCase(product.status())
                && !"0".equals(product.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "商品已下架或售出");
        }
        int quantity = request.quantity() == null || request.quantity() <= 0 ? 1 : request.quantity();
        CartRow existing = findRow(userId, goodsId);
        if (existing == null) {
            jdbc.sql("""
                    INSERT INTO cart_item(user_id, goods_id, quantity, selected)
                    VALUES (:userId, :goodsId, :quantity, TRUE)
                    """)
                    .param("userId", userId)
                    .param("goodsId", goodsId)
                    .param("quantity", quantity)
                    .update();
        } else {
            jdbc.sql("UPDATE cart_item SET quantity=quantity+:quantity, selected=TRUE WHERE cart_id=:cartId AND user_id=:userId")
                    .param("quantity", quantity)
                    .param("cartId", existing.cartId())
                    .param("userId", userId)
                    .update();
        }
        return list(authorization);
    }

    public List<CartItemView> updateQuantity(String authorization, long cartId, UpdateCartRequest request) {
        long userId = users.requireLogin(authorization).userId();
        requireOwn(userId, cartId);
        int quantity = request == null || request.quantity() == null ? 0 : request.quantity();
        if (quantity <= 0) {
            jdbc.sql("DELETE FROM cart_item WHERE cart_id=:cartId AND user_id=:userId")
                    .param("cartId", cartId).param("userId", userId).update();
        } else {
            jdbc.sql("UPDATE cart_item SET quantity=:quantity WHERE cart_id=:cartId AND user_id=:userId")
                    .param("quantity", quantity).param("cartId", cartId).param("userId", userId).update();
        }
        return list(authorization);
    }

    public List<CartItemView> remove(String authorization, long cartId) {
        long userId = users.requireLogin(authorization).userId();
        requireOwn(userId, cartId);
        jdbc.sql("DELETE FROM cart_item WHERE cart_id=:cartId AND user_id=:userId")
                .param("cartId", cartId).param("userId", userId).update();
        return list(authorization);
    }

    public List<CartItemView> select(String authorization, long cartId, SelectCartRequest request) {
        long userId = users.requireLogin(authorization).userId();
        requireOwn(userId, cartId);
        boolean selected = request == null || request.selected() == null || request.selected();
        jdbc.sql("UPDATE cart_item SET selected=:selected WHERE cart_id=:cartId AND user_id=:userId")
                .param("selected", selected).param("cartId", cartId).param("userId", userId).update();
        return list(authorization);
    }

    private CatalogClient.ProductSnapshot requireProduct(long goodsId) {
        try {
            return catalog.getProduct(goodsId);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 422 || exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
            }
            throw exception;
        }
    }

    private void requireOwn(long userId, long cartId) {
        CartRow row = jdbc.sql("""
                SELECT cart_id, user_id, goods_id, quantity, selected
                FROM cart_item WHERE cart_id=:cartId AND user_id=:userId
                """)
                .param("cartId", cartId).param("userId", userId)
                .query(CartRow.class).optional().orElse(null);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "购物车记录不存在");
        }
    }

    private CartRow findRow(long userId, long goodsId) {
        return jdbc.sql("""
                SELECT cart_id, user_id, goods_id, quantity, selected
                FROM cart_item WHERE user_id=:userId AND goods_id=:goodsId
                """)
                .param("userId", userId).param("goodsId", goodsId)
                .query(CartRow.class).optional().orElse(null);
    }

    private List<CartRow> rows(long userId) {
        return jdbc.sql("""
                SELECT cart_id, user_id, goods_id, quantity, selected
                FROM cart_item WHERE user_id=:userId
                ORDER BY cart_id DESC
                """)
                .param("userId", userId)
                .query(CartRow.class).list();
    }

    private CartItemView toView(CartRow row) {
        CatalogClient.ProductSnapshot product = snapshotOrNull(row.goodsId());
        boolean valid = product != null && ("ON_SALE".equals(product.status())
                || "approved".equalsIgnoreCase(product.status()) || "0".equals(product.status()));
        String title = product == null ? "商品" : product.name();
        String cover = product == null ? "" : product.cover();
        BigDecimal price = product == null || product.price() == null ? BigDecimal.ZERO : product.price();
        int quantity = Math.max(1, row.quantity());
        boolean selected = row.selected() == null || row.selected();
        return new CartItemView(
                row.cartId(),
                row.goodsId(),
                title == null || title.isBlank() ? "商品" : title,
                cover,
                price,
                quantity,
                selected,
                price.multiply(BigDecimal.valueOf(quantity)),
                product == null ? "松果集市卖家" : product.shopName(),
                product == null || product.scene() == null || product.scene().isBlank() ? "used" : product.scene(),
                "",
                valid);
    }

    private CatalogClient.ProductSnapshot snapshotOrNull(long goodsId) {
        try {
            return catalog.getProduct(goodsId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record AddCartRequest(Long goodsId, Integer quantity) {}
    public record UpdateCartRequest(Integer quantity) {}
    public record SelectCartRequest(Boolean selected) {}
    public record CartItemView(
            long cartId,
            long goodsId,
            String title,
            String cover,
            BigDecimal price,
            int quantity,
            boolean selected,
            BigDecimal subtotal,
            String shopName,
            String scene,
            String category,
            boolean valid) {}
    private record CartRow(long cartId, long userId, long goodsId, int quantity, Boolean selected) {}
}
