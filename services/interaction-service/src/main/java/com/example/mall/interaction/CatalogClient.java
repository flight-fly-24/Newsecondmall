package com.example.mall.interaction;

import com.example.mall.interaction.TopicService.ProductCard;
import com.example.mall.interaction.TopicService.StoreCard;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogClient {
    private final RestClient client;
    private final CircuitBreaker circuitBreaker;

    public CatalogClient(@Qualifier("catalogRestClient") RestClient catalogRestClient) {
        this(catalogRestClient, CircuitBreaker.ofDefaults("catalog"));
    }

    @Autowired
    public CatalogClient(@Qualifier("catalogRestClient") RestClient catalogRestClient,
                         CircuitBreaker catalogCircuitBreaker) {
        this.client = catalogRestClient;
        this.circuitBreaker = catalogCircuitBreaker;
    }

    public TopicService.ProductCard product(Integer productId) {
        if (productId == null) {
            return null;
        }
        Map<?, ?> body = getMap("/api/products/" + productId);
        if (body == null) {
            return null;
        }
        String id = text(body, "id", "productId");
        String title = text(body, "title", "name");
        String cover = text(body, "cover", "image");
        String category = text(body, "category");
        BigDecimal price = decimal(body.get("price"));
        if (id.isBlank() && title.isBlank()) {
            return null;
        }
        return new TopicService.ProductCard(id.isBlank() ? String.valueOf(productId) : id, title, cover, price, category);
    }

    public TopicService.StoreCard store(Integer storeId) {
        if (storeId == null) {
            return null;
        }
        Map<?, ?> body = getMap("/api/stores/" + storeId);
        if (body == null) {
            return null;
        }
        String id = text(body, "id", "storeId");
        String name = text(body, "name", "storeName");
        String score = text(body, "score");
        String fans = text(body, "fans", "followerCount");
        String desc = text(body, "desc", "storeDesc");
        String badge = text(body, "badge");
        String avatar = text(body, "avatar");
        if (id.isBlank() && name.isBlank()) {
            return null;
        }
        return new TopicService.StoreCard(id.isBlank() ? String.valueOf(storeId) : id, name, score, fans, desc, badge, avatar);
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> getMap(String path) {
        try {
            // 熔断打开时 executeSupplier 直接抛 CallNotPermittedException，被下方 catch 吞掉并返回 null，
            // 保持原有降级语义：商品/店铺卡片隐藏，帖子正文仍展示
            Map<?, ?> body = circuitBreaker.executeSupplier(() ->
                    client.get().uri(path).retrieve().body(Map.class));
            if (body == null) {
                return null;
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> nested) {
                return nested;
            }
            return body;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String text(Map<?, ?> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value != null) {
            try {
                return new BigDecimal(String.valueOf(value));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
