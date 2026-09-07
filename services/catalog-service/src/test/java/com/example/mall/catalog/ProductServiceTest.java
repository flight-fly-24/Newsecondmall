package com.example.mall.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
class ProductServiceTest {
    @Autowired ProductService service;
    @Autowired StoreService stores;
    @Autowired ProductRelationService relations;

    @Test
    void ownsAndReturnsItsProductData() {
        ProductService.ProductView created = service.create(
                new ProductService.CreateProduct(22, "二手显示器", new BigDecimal("620.00")));
        assertThat(service.find(created.productId()).sellerId()).isEqualTo(22);
        assertThat(service.list()).extracting(ProductService.ProductView::name).contains("二手显示器");
    }

    @Test
    void sellerCanListAndUpdateOwnProductAndStoreCanBeQueried() {
        ProductService.ProductView created = service.create(
                new ProductService.CreateProduct(31, "旧标题", new BigDecimal("100.00")));

        ProductService.ProductView updated = service.update(created.productId(),
                new ProductService.UpdateProduct("新标题", "数码", "描述", "九成新", "故事",
                        new BigDecimal("88.00"), new BigDecimal("75.00"), "武汉", "/image.png"),
                new CatalogUser(31, "seller", "seller31"));

        assertThat(updated.name()).isEqualTo("新标题");
        assertThat(service.mine(31)).extracting(ProductService.ProductView::productId)
                .contains(created.productId());
        StoreService.StoreView store = stores.mine(31);
        assertThat(store.productCount()).isEqualTo(1);
        assertThat(stores.products(store.storeId())).extracting(ProductService.ProductView::name)
                .containsExactly("新标题");
    }

    @Test
    void sellerCannotUpdateAnotherSellersProduct() {
        ProductService.ProductView created = service.create(
                new ProductService.CreateProduct(41, "不可越权商品", new BigDecimal("50.00")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.update(created.productId(),
                new ProductService.UpdateProduct("越权修改", "数码", null, null, null,
                        new BigDecimal("20.00"), null, null, null),
                new CatalogUser(42, "seller", "seller42")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void favoriteIsIdempotentAndHistoryCanBeCleared() {
        ProductService.ProductView created = service.create(
                new ProductService.CreateProduct(51, "关系测试商品", new BigDecimal("66.00")));

        relations.add("favorite", 501, created.productId());
        relations.add("favorite", 501, created.productId());
        relations.add("history", 501, created.productId());

        assertThat(relations.list("favorite", 501)).hasSize(1);
        assertThat(relations.list("history", 501)).hasSize(1);
        relations.clear("history", 501);
        assertThat(relations.list("history", 501)).isEmpty();
    }

//     @Test
//     void listFiltersBySceneAndKeywordAndExposesStorefrontFields() {
//         service.create(new ProductService.CreateProduct(61, "二手键盘", new BigDecimal("80.00")));
//         ProductService.ProductView created = service.publish(
//                 new ProductService.PublishRequest("used", "九成新显示器", "/cover.png", "数码",
//                         new BigDecimal("620.00"), "九成新", "屏幕完好", "毕业出闲置", new BigDecimal("500.00"), "武汉"),
//                 new CatalogUser(71, "seller", "seller71"));

//         assertThat(service.list("used", "显示器")).extracting(ProductService.ProductView::productId)
//                 .doesNotContain(created.productId());
//         assertThat(service.pending()).extracting(ProductService.ProductView::productId)
//                 .contains(created.productId());
//         assertThat(service.audit(created.productId(), "approve", "测试通过").status()).isEqualTo("approved");
//         assertThat(service.list("used", "显示器")).extracting(ProductService.ProductView::productId)
//                 .contains(created.productId());
//         assertThat(service.list("new", null)).extracting(ProductService.ProductView::productId)
//                 .doesNotContain(created.productId());

//         ProductService.StorefrontProduct view = service.toStorefront(created);
//         assertThat(view.id()).isEqualTo(String.valueOf(created.productId()));
//         assertThat(view.title()).isEqualTo("九成新显示器");
//         assertThat(view.cover()).isEqualTo("/cover.png");
//         assertThat(view.shopName()).isNotBlank();
//         assertThat(view.status()).isEqualTo("approved");
//         assertThat(view.scene()).isEqualTo("used");
//     }
    @Test
        void listFiltersBySceneAndKeywordAndExposesStorefrontFields() {
                service.create(new ProductService.CreateProduct(
                        61, "二手键盘", new BigDecimal("80.00")));

                ProductService.ProductView created = service.publish(
                        new ProductService.PublishRequest(
                                "used",
                                "九成新显示器",
                                "/cover.png",
                                "数码",
                                new BigDecimal("620.00"),
                                "九成新",
                                "屏幕完好",
                                "毕业出闲置",
                                new BigDecimal("500.00"),
                                "武汉"),
                        new CatalogUser(71, "seller", "seller71"));

                assertThat(service.list("used", "显示器"))
                        .extracting(ProductService.ProductView::productId)
                        .doesNotContain(created.productId());

                assertThat(service.pending())
                        .extracting(ProductService.ProductView::productId)
                        .contains(created.productId());

                assertThat(service.audit(created.productId(), "approve", "测试通过").status())
                        .isEqualTo("approved");

                ProductService.ProductView approved = service.find(created.productId());

                assertThat(service.list("used", "显示器"))
                        .extracting(ProductService.ProductView::productId)
                        .contains(created.productId());

                assertThat(service.list("new", null))
                        .extracting(ProductService.ProductView::productId)
                        .doesNotContain(created.productId());

                ProductService.StorefrontProduct view = service.toStorefront(approved);

                assertThat(view.id()).isEqualTo(String.valueOf(created.productId()));
                assertThat(view.title()).isEqualTo("九成新显示器");
                assertThat(view.cover()).isEqualTo("/cover.png");
                assertThat(view.shopName()).isNotBlank();
                assertThat(view.status()).isEqualTo("approved");
                assertThat(view.scene()).isEqualTo("used");
        }

    @Test
    void buyerCannotPublish() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.publish(
                new ProductService.PublishRequest("used", "标题", "/a.png", "数码",
                        new BigDecimal("10.00"), "全新", "描述", "故事", null, "武汉"),
                new CatalogUser(81, "buyer", "buyer81")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void auditRejectsInvalidActionAndCannotRepeat() {
        ProductService.ProductView created = service.publish(
                new ProductService.PublishRequest("new", "审核商品", "/cover.png", "数码",
                        new BigDecimal("99.00"), null, "描述", null, null, "武汉"),
                new CatalogUser(72, "seller", "seller72"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.audit(created.productId(), "invalid", ""))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        assertThat(service.audit(created.productId(), "reject", "描述不完整").status()).isEqualTo("rejected");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.audit(created.productId(), "approve", ""))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void missingProductAndStoreReturnNotFound() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.find(999999))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> stores.find(999999))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void unsupportedRelationTypeIsRejectedForEveryOperation() {
        ProductService.ProductView created = service.create(
                new ProductService.CreateProduct(91, "类型测试", new BigDecimal("12.00")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> relations.list("unknown", 1))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> relations.add("unknown", 1, created.productId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> relations.clear(null, 1))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void historyKeepsRepeatedVisitsWhileFavoriteStaysUnique() {
        ProductService.ProductView created = service.create(
                new ProductService.CreateProduct(92, "访问历史", new BigDecimal("33.00")));
        relations.add("history", 900, created.productId());
        relations.add("history", 900, created.productId());
        relations.add("favorite", 900, created.productId());
        relations.add("favorite", 900, created.productId());
        assertThat(relations.list("history", 900)).hasSize(2);
        assertThat(relations.list("favorite", 900)).hasSize(1);
        relations.clear("favorite", 900);
        assertThat(relations.list("favorite", 900)).isEmpty();
    }
}
