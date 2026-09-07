package com.example.mall.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

public interface CatalogClient {
    ProductSnapshot getProduct(long productId);
    void markSold(long productId, String orderNumber);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductSnapshot(long productId, long sellerId, String name, BigDecimal price, String status,
                           String storeName, String image, String scene) {
        public ProductSnapshot(long productId, long sellerId, String name, BigDecimal price, String status) {
            this(productId, sellerId, name, price, status, "卖家", "", "used");
        }

        public String shopName() {
            return storeName == null || storeName.isBlank() ? "卖家" : storeName;
        }

        public String cover() {
            return image == null ? "" : image;
        }
    }
}
