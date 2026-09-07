package com.example.mall.order;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {
    private final JdbcClient jdbc;
    private final CatalogClient catalog;
    private final UserClient users;

    public OrderService(JdbcClient jdbc, CatalogClient catalog, UserClient users) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.users = users;
    }

    public OrderView create(CreateOrder request) {
        Optional<OrderView> existing = findByRequestId(request.clientRequestId());
        if (existing.isPresent()) {
            return completeProductMark(existing.get());
        }

        if (request.addressId() > 0) {
            users.requireActiveUserAndAddress(request.buyerId(), request.addressId());
        } else {
            users.requireActiveUser(request.buyerId());
        }
        CatalogClient.ProductSnapshot product = catalog.getProduct(request.productId());
        if (!("ON_SALE".equalsIgnoreCase(product.status())
                || "approved".equalsIgnoreCase(product.status())
                || "0".equals(product.status()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "product is not on sale");
        }
        if (product.sellerId() == request.buyerId()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能购买自己发布的商品");
        }

        jdbc.sql("""
                INSERT INTO orders(client_request_id, buyer_id, seller_id, product_id,
                    product_name, unit_price, quantity, amount, status, shop_name, cover, scene)
                VALUES (:requestId, :buyerId, :sellerId, :productId, :name, :price, :quantity, :amount,
                    'PENDING_PRODUCT_MARK', :shopName, :cover, :scene)
                """)
                .param("requestId", request.clientRequestId())
                .param("buyerId", request.buyerId())
                .param("sellerId", product.sellerId())
                .param("productId", product.productId())
                .param("name", product.name())
                .param("price", product.price())
                .param("quantity", request.quantity())
                .param("amount", product.price().multiply(BigDecimal.valueOf(request.quantity())))
                .param("shopName", product.shopName())
                .param("cover", product.cover())
                .param("scene", product.scene() == null ? "used" : product.scene())
                .update();
        return completeProductMark(findByRequestId(request.clientRequestId()).orElseThrow());
    }

    public List<StorefrontOrderView> createFromItems(String authorization, List<OrderItem> items) {
        UserClient.CurrentUser user = users.requireLogin(authorization);
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单商品不能为空");
        }
        for (OrderItem item : items) {
            long goodsId = parseGoodsId(item.goodsId());
            int quantity = item.quantity() == null || item.quantity() <= 0 ? 1 : item.quantity();
            create(new CreateOrder(frontendRequestId(user.userId(), goodsId), user.userId(), 0, goodsId, quantity));
        }
        return listStorefront(user.userId(), null);
    }

    public List<StorefrontOrderView> listStorefront(String authorization, String status) {
        UserClient.CurrentUser user = users.requireLogin(authorization);
        return listStorefront(user.userId(), status);
    }

    public StorefrontOrderView cancel(String authorization, String orderId) {
        UserClient.CurrentUser user = users.requireLogin(authorization);
        OrderRecord order = requireOwnOrder(parseOrderId(orderId), user.userId());
        if (isCancelled(order.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已取消");
        }
        jdbc.sql("UPDATE orders SET status='CANCELLED' WHERE order_id=:id")
                .param("id", order.orderId()).update();
        return toStorefront(loadRecord(order.orderId()));
    }

    public StorefrontOrderView review(String authorization, String orderId, ReviewRequest request) {
        UserClient.CurrentUser user = users.requireLogin(authorization);
        OrderRecord order = requireOwnOrder(parseOrderId(orderId), user.userId());
        if (!isConfirmed(order.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单完成后才能评价");
        }
        if (order.reviewed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单已评价");
        }
        int productScore = normalizeScore(request == null ? null : request.productScore());
        int sellerScore = normalizeScore(request == null ? null : request.sellerScore());
        String content = request == null || request.content() == null ? "" : request.content().trim();
        if (content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评价内容不能为空");
        }
        jdbc.sql("""
                INSERT INTO product_review(order_id, goods_id, buyer_id, seller_id, product_score, seller_score, content)
                VALUES (:orderId, :goodsId, :buyerId, :sellerId, :productScore, :sellerScore, :content)
                """)
                .param("orderId", order.orderId())
                .param("goodsId", order.productId())
                .param("buyerId", order.buyerId())
                .param("sellerId", order.sellerId())
                .param("productScore", productScore)
                .param("sellerScore", sellerScore)
                .param("content", content)
                .update();
        return toStorefront(loadRecord(order.orderId()));
    }

    private OrderView completeProductMark(OrderView order) {
        if ("CONFIRMED".equals(order.status()) || "CANCELLED".equals(order.status())) return order;
        try {
            catalog.markSold(order.productId(), order.clientRequestId());
            jdbc.sql("UPDATE orders SET status='CONFIRMED' WHERE order_id=:id")
                    .param("id", order.orderId()).update();
            return find(order.orderId());
        } catch (ResponseStatusException exception) {
            jdbc.sql("""
                    UPDATE orders
                    SET compensation_attempts=compensation_attempts+1,
                        status=CASE WHEN compensation_attempts + 1 >= 3 THEN 'CANCELLED' ELSE 'COMPENSATION_REQUIRED' END
                    WHERE order_id=:id
                    """).param("id", order.orderId()).update();
            throw exception;
        }
    }

    public OrderView find(long id) {
        return jdbc.sql(ORDER_COLUMNS + " WHERE order_id=:id").param("id", id).query(OrderView.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    }

    public OrderParticipants participants(long orderId) {
        OrderView order = find(orderId);
        return new OrderParticipants(order.orderId(), order.buyerId(), order.sellerId());
    }

    public SellerSummary sellerSummary(long sellerId) {
        SellerSummaryRow row = jdbc.sql("""
                SELECT COUNT(*) AS orderCount,
                    COALESCE(SUM(CASE WHEN status IN ('PENDING_PRODUCT_MARK', 'COMPENSATION_REQUIRED') THEN 1 ELSE 0 END), 0) AS pendingShipCount,
                    COALESCE(SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END), 0) AS confirmedCount,
                    COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelledCount,
                    COALESCE(SUM(CASE WHEN status = 'CONFIRMED' THEN amount ELSE 0 END), 0) AS totalAmount
                FROM orders
                WHERE seller_id=:sellerId
                """)
                .param("sellerId", sellerId)
                .query(SellerSummaryRow.class)
                .optional()
                .orElse(new SellerSummaryRow(0, 0, 0, 0, BigDecimal.ZERO));
        return new SellerSummary(sellerId, row.orderCount(), row.pendingShipCount(), row.confirmedCount(),
                row.cancelledCount(), row.totalAmount() == null ? BigDecimal.ZERO : row.totalAmount());
    }

    private Optional<OrderView> findByRequestId(String requestId) {
        return jdbc.sql(ORDER_COLUMNS + " WHERE client_request_id=:requestId")
                .param("requestId", requestId).query(OrderView.class).optional();
    }

    private List<StorefrontOrderView> listStorefront(long buyerId, String status) {
        String normalized = normalizeListStatus(status);
        return jdbc.sql(ORDER_RECORD_COLUMNS + """
                WHERE o.buyer_id=:buyerId
                  AND (:status IS NULL OR o.status=:status)
                ORDER BY o.created_at DESC, o.order_id DESC
                """)
                .param("buyerId", buyerId)
                .param("status", normalized)
                .query(OrderRecord.class)
                .list()
                .stream()
                .map(this::toStorefront)
                .toList();
    }

    private OrderRecord requireOwnOrder(long orderId, long buyerId) {
        OrderRecord order = loadRecord(orderId);
        if (order.buyerId() != buyerId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private OrderRecord loadRecord(long orderId) {
        return jdbc.sql(ORDER_RECORD_COLUMNS + " WHERE o.order_id=:id")
                .param("id", orderId)
                .query(OrderRecord.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
    }

    private StorefrontOrderView toStorefront(OrderRecord order) {
        boolean cancelled = isCancelled(order.status());
        boolean reviewed = order.reviewed();
        boolean confirmed = isConfirmed(order.status());
        String status = cancelled ? "已取消" : reviewed ? "已评价" : confirmed ? "已完成" : "待收货";
        String shop = order.shopName() == null || order.shopName().isBlank() ? "卖家" : order.shopName();
        String title = order.productName() == null || order.productName().isBlank() ? "商品" : order.productName();
        return new StorefrontOrderView(
                String.valueOf(order.orderId()),
                shop,
                status,
                title,
                order.cover() == null ? "" : order.cover(),
                "new".equals(order.scene()) ? "新品" : "二手",
                "平台担保",
                order.amount() == null ? BigDecimal.ZERO : order.amount(),
                String.valueOf(order.productId()),
                confirmed && !reviewed,
                reviewed,
                order.productScore(),
                order.sellerScore(),
                order.reviewContent() == null ? "" : order.reviewContent()
        );
    }

    private static boolean isConfirmed(String status) {
        return "CONFIRMED".equals(status) || "completed".equals(status);
    }

    private static boolean isCancelled(String status) {
        return "CANCELLED".equals(status) || "cancelled".equals(status);
    }

    private static String frontendRequestId(long buyerId, long goodsId) {
        return "web-" + buyerId + "-" + goodsId;
    }

    private static long parseGoodsId(String goodsId) {
        try {
            return Long.parseLong(goodsId);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
    }

    private static long parseOrderId(String orderId) {
        try {
            return Long.parseLong(orderId);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
    }

    private static int normalizeScore(Integer score) {
        if (score == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评分不能为空");
        }
        return Math.max(1, Math.min(5, score));
    }

    private static String normalizeListStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.trim()) {
            case "已完成", "completed", "CONFIRMED" -> "CONFIRMED";
            case "已取消", "cancelled", "CANCELLED" -> "CANCELLED";
            case "待收货", "COMPENSATION_REQUIRED", "PENDING_PRODUCT_MARK" -> status.trim();
            default -> null;
        };
    }

    private static final String ORDER_COLUMNS = """
            SELECT order_id, client_request_id, buyer_id, seller_id, product_id,
                product_name, unit_price, quantity, amount, status
            FROM orders
            """;

    private static final String ORDER_RECORD_COLUMNS = """
            SELECT o.order_id, o.client_request_id, o.buyer_id, o.seller_id, o.product_id AS productId,
                o.product_name AS productName, o.unit_price AS unitPrice, o.quantity, o.amount, o.status,
                o.shop_name AS shopName, o.cover, o.scene,
                r.product_score AS productScore, r.seller_score AS sellerScore, r.content AS reviewContent
            FROM orders o
            LEFT JOIN product_review r ON r.order_id = o.order_id
            """;

    public record CreateOrder(String clientRequestId, long buyerId, long addressId, long productId, int quantity) {}
    public record OrderItem(String goodsId, Integer quantity) {}
    public record ReviewRequest(Integer productScore, Integer sellerScore, String content) {}
    public record OrderView(long orderId, String clientRequestId, long buyerId, long sellerId, long productId,
                            String productName, BigDecimal unitPrice, int quantity, BigDecimal amount, String status) {}
    public record OrderRecord(long orderId, String clientRequestId, long buyerId, long sellerId, long productId,
                              String productName, BigDecimal unitPrice, int quantity, BigDecimal amount, String status,
                              String shopName, String cover, String scene,
                              Integer productScore, Integer sellerScore, String reviewContent) {
        boolean reviewed() {
            return productScore != null || (reviewContent != null && !reviewContent.isBlank());
        }
    }
    public record StorefrontOrderView(String id, String shop, String status, String title, String cover, String type,
                                      String service, BigDecimal amount, String goodsId, boolean reviewable,
                                      boolean reviewed, Integer productScore, Integer sellerScore, String reviewContent) {}
    public record OrderParticipants(long orderId, long buyerId, long sellerId) {
        public boolean includes(long userId) {
            return userId == buyerId || userId == sellerId;
        }
    }
    public record SellerSummary(long sellerId, long orderCount, long pendingShipCount, long confirmedCount,
                               long cancelledCount, BigDecimal totalAmount) {}
    private record SellerSummaryRow(long orderCount, long pendingShipCount, long confirmedCount, long cancelledCount,
                                   BigDecimal totalAmount) {}
}
