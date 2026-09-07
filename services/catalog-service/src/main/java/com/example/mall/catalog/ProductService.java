package com.example.mall.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductService {
    static final String PRODUCT_COLUMNS = """
            SELECT goods_id AS product_id, seller_id, name, price, status, category,
                   description, goods_condition AS `condition`, story, floor_price,
                   scene, location, image, store_id, store_name, created_at
            FROM goods
            """;

    private final JdbcClient jdbc;

    public ProductService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public ProductView create(CreateProduct request) {
        long storeId = ensureStore(request.sellerId());
        jdbc.sql("""
                INSERT INTO goods(seller_id, name, price, status, store_id, store_name)
                SELECT :sellerId, :name, :price, 'ON_SALE', store_id, store_name
                FROM store WHERE store_id=:storeId
                """).param("sellerId", request.sellerId()).param("name", request.name())
                .param("price", request.price()).param("storeId", storeId).update();
        return jdbc.sql(PRODUCT_COLUMNS + " ORDER BY goods_id DESC LIMIT 1").query(ProductView.class).single();
    }

    public ProductView find(long id) {
        return jdbc.sql(PRODUCT_COLUMNS + " WHERE goods_id=:id").param("id", id)
                .query(ProductView.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
    }

    public List<ProductView> list() {
        return list(null, null);
    }

    public List<ProductView> list(String scene, String keyword) {
        String sceneFilter = scene == null ? "" : scene.trim();
        String keywordFilter = keyword == null ? "" : keyword.trim();
        return jdbc.sql(PRODUCT_COLUMNS + """
                WHERE status IN ('ON_SALE', 'approved', '0')
                  AND (:scene = '' OR :scene = 'all' OR scene = :scene)
                  AND (:keyword = ''
                       OR name LIKE CONCAT('%', :keyword, '%')
                       OR COALESCE(description, '') LIKE CONCAT('%', :keyword, '%')
                       OR COALESCE(category, '') LIKE CONCAT('%', :keyword, '%')
                       OR COALESCE(store_name, '') LIKE CONCAT('%', :keyword, '%'))
                ORDER BY created_at DESC, goods_id DESC
                """)
                .param("scene", sceneFilter)
                .param("keyword", keywordFilter)
                .query(ProductView.class).list();
    }

    public List<ProductView> mine(long sellerId) {
        return jdbc.sql(PRODUCT_COLUMNS + " WHERE seller_id=:sellerId ORDER BY created_at DESC, goods_id DESC")
                .param("sellerId", sellerId).query(ProductView.class).list();
    }

    @Transactional
    public ProductView update(long id, UpdateProduct request, CatalogUser user) {
        ProductView existing = find(id);
        if (!user.isSeller()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only sellers can edit products");
        }
        if (existing.sellerId() != user.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot edit another seller's product");
        }
        int changed = jdbc.sql("""
                UPDATE goods SET name=:name, category=:category, description=:description,
                    goods_condition=:condition, story=:story, price=:price, floor_price=:floorPrice,
                    location=:location, image=:image, updated_at=CURRENT_TIMESTAMP
                WHERE goods_id=:id AND seller_id=:sellerId
                """)
                .param("name", request.name()).param("category", request.category())
                .param("description", request.description()).param("condition", request.condition())
                .param("story", request.story()).param("price", request.price())
                .param("floorPrice", request.floorPrice()).param("location", request.location())
                .param("image", request.image()).param("id", id).param("sellerId", user.userId()).update();
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "product was not updated");
        }
        return find(id);
    }

    @Transactional
    public ProductView publish(PublishRequest request, CatalogUser user) {
        if (!user.isSeller()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前仅卖家可以发布商品");
        }
        if (request == null || isBlank(request.title()) || isBlank(request.image())
                || isBlank(request.category()) || request.price() == null
                || isBlank(request.description())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品参数不能为空");
        }
        String scene = "new".equals(request.scene()) ? "new" : "used";
        if ("used".equals(scene) && (isBlank(request.condition()) || isBlank(request.story()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "二手商品必须填写成色和故事");
        }
        long storeId = ensureStore(user.userId());
        jdbc.sql("""
                INSERT INTO goods(seller_id, name, price, status, store_id, store_name, category, description,
                    goods_condition, story, floor_price, scene, location, image)
                SELECT :sellerId, :name, :price, 'pending', store_id, store_name, :category, :description,
                    :condition, :story, :floorPrice, :scene, :location, :image
                FROM store WHERE store_id=:storeId
                """)
                .param("sellerId", user.userId())
                .param("name", request.title().trim())
                .param("price", request.price())
                .param("category", request.category().trim())
                .param("description", request.description().trim())
                .param("condition", defaultText(request.condition(), "待补充"))
                .param("story", defaultText(request.story(), request.description().trim()))
                .param("floorPrice", request.floorPrice())
                .param("scene", scene)
                .param("location", defaultText(request.location(), "未知地区"))
                .param("image", request.image().trim())
                .param("storeId", storeId)
                .update();
        return jdbc.sql(PRODUCT_COLUMNS + " WHERE seller_id=:sellerId ORDER BY goods_id DESC LIMIT 1")
                .param("sellerId", user.userId())
                .query(ProductView.class).single();
    }

    public List<ProductView> pending() {
        return jdbc.sql(PRODUCT_COLUMNS + " WHERE LOWER(status)='pending' ORDER BY created_at, goods_id")
                .query(ProductView.class).list();
    }

    @Transactional
    public AuditResult audit(long id, String action, String reason) {
        String normalized = action == null ? "" : action.trim().toLowerCase();
        String status;
        if ("approve".equals(normalized)) status = "approved";
        else if ("reject".equals(normalized)) status = "rejected";
        else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "审核动作必须是 approve 或 reject");
        int changed = jdbc.sql("UPDATE goods SET status=:status, updated_at=CURRENT_TIMESTAMP WHERE goods_id=:id AND LOWER(status)='pending'")
                .param("status", status).param("id", id).update();
        if (changed == 0) {
            int count = jdbc.sql("SELECT COUNT(*) FROM goods WHERE goods_id=:id").param("id", id).query(Integer.class).single();
            if (count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "商品已完成审核");
        }
        return new AuditResult(String.valueOf(id), status, defaultText(reason, ""));
    }

    public StorefrontProduct toStorefront(ProductView product) {
        return toStorefront(product, 100);
    }

    public StorefrontProduct toStorefront(ProductView product, int credit) {
        String scene = "new".equals(product.scene()) ? "new" : "used";
        String status = storefrontStatus(product.status());
        String title = defaultText(product.name(), "未命名商品");
        String description = defaultText(product.description(), "卖家暂未填写详细描述");
        String condition = defaultText(product.condition(), "待补充");
        BigDecimal price = product.price() == null ? BigDecimal.ZERO : product.price();
        BigDecimal originPrice = price.add("used".equals(scene) ? new BigDecimal("80") : new BigDecimal("120"));
        String shopName = defaultText(product.storeName(), "个人卖家");
        String publishedAt = product.createdAt() == null ? "" : product.createdAt().toString().replace('T', ' ');
        return new StorefrontProduct(
                String.valueOf(product.productId()),
                scene,
                defaultText(product.category(), "未分类"),
                title,
                sceneLabel(scene) + " " + condition + " · " + statusTag(status),
                price,
                originPrice,
                defaultText(product.image(), ""),
                statusTag(status),
                condition,
                credit,
                defaultText(product.location(), "未知地区"),
                shopName,
                "发布后由卖家设置配送方式",
                List.of("平台担保", statusTag(status)),
                List.of("真实描述", "pending".equals(status) ? "待审核" : "平台审核记录"),
                defaultText(product.story(), description),
                List.of(),
                List.of(),
                List.of(),
                List.of("可询问成色和配件", "可生成验货清单", "可根据最低价辅助议价"),
                status,
                product.sellerId(),
                shopName,
                publishedAt,
                "",
                product.floorPrice(),
                description,
                product.storeId() == null ? "" : String.valueOf(product.storeId()));
    }

    public void markSold(long id, String orderNumber) {
        int changed = jdbc.sql("""
                UPDATE goods SET status='SOLD', sold_order_number=:orderNumber, updated_at=CURRENT_TIMESTAMP
                WHERE goods_id=:id AND (status IN ('ON_SALE', 'approved', '0') OR sold_order_number=:orderNumber)
                """).param("id", id).param("orderNumber", orderNumber).update();
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "product is not available");
        }
    }

    private long ensureStore(long sellerId) {
        return jdbc.sql("SELECT store_id FROM store WHERE seller_id=:sellerId ORDER BY store_id LIMIT 1")
                .param("sellerId", sellerId).query(Long.class).optional().orElseGet(() -> {
                    String sellerName = "卖家 " + sellerId;
                    jdbc.sql("""
                            INSERT INTO store(seller_id, seller_name, store_name, status)
                            VALUES (:sellerId, :sellerName, :storeName, 'normal')
                            """).param("sellerId", sellerId).param("sellerName", sellerName)
                            .param("storeName", sellerName + " 的店铺").update();
                    return jdbc.sql("SELECT store_id FROM store WHERE seller_id=:sellerId ORDER BY store_id LIMIT 1")
                            .param("sellerId", sellerId).query(Long.class).single();
                });
    }

    private static String storefrontStatus(String status) {
        if (status == null || status.isBlank()) {
            return "approved";
        }
        String normalized = status.trim();
        if ("0".equals(normalized) || "ON_SALE".equalsIgnoreCase(normalized) || "approved".equalsIgnoreCase(normalized)) {
            return "approved";
        }
        if ("2".equals(normalized) || "pending".equalsIgnoreCase(normalized) || normalized.toLowerCase().startsWith("pe")) {
            return "pending";
        }
        if ("3".equals(normalized) || "rejected".equalsIgnoreCase(normalized)) {
            return "rejected";
        }
        if ("SOLD".equalsIgnoreCase(normalized)) {
            return "approved";
        }
        return "approved";
    }

    private static String statusTag(String status) {
        if ("pending".equals(status)) {
            return "待审核";
        }
        if ("rejected".equals(status)) {
            return "已拒绝";
        }
        return "审核通过";
    }

    private static String sceneLabel(String scene) {
        return "new".equals(scene) ? "新品" : "二手";
    }

    private static String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record PublishRequest(String scene, String title, String image, String category, BigDecimal price,
                                 String condition, String description, String story, BigDecimal floorPrice,
                                 String location) {}
    public record AuditResult(String id, String status, String reason) {}

    public record StorefrontProduct(
            String id, String scene, String category, String title, String subtitle, BigDecimal price,
            BigDecimal originPrice, String cover, String tag, String condition, int credit, String location,
            String shopName, String delivery, List<String> service, List<String> highlights, String story,
            List<Object> params, List<Object> reviews, List<Object> timeline, List<String> aiTips, String status,
            Long publisherId, String publisherName, String publishedAt, String rejectReason, BigDecimal floorPrice,
            String description, String storeId) {}

    public record CreateProduct(long sellerId, String name, BigDecimal price) {}
    public record UpdateProduct(String name, String category, String description, String condition,
                                String story, BigDecimal price, BigDecimal floorPrice, String location, String image) {}
    public record ProductView(long productId, long sellerId, String name, BigDecimal price, String status,
                              String category, String description, String condition, String story,
                              BigDecimal floorPrice, String scene, String location, String image,
                              Long storeId, String storeName, LocalDateTime createdAt) {
        public String id() { return String.valueOf(productId); }
        public String title() { return name; }
        public String cover() { return image; }
        public long publisherId() { return sellerId; }
        public String shopName() { return storeName; }
        public String publishedAt() { return createdAt == null ? "" : createdAt.toString(); }
    }
}
