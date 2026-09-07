package com.example.mall.catalog;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;
    private final HttpAuthClient authClient;

    public ProductController(ProductService service, HttpAuthClient authClient) {
        this.service = service;
        this.authClient = authClient;
    }

    @GetMapping
    public ApiResult<List<ProductService.StorefrontProduct>> list(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(service.list(scene, keyword).stream().map(service::toStorefront).toList());
    }

    @GetMapping("/mine")
    public ApiResult<List<ProductService.StorefrontProduct>> mine(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        CatalogUser user = authClient.requireUser(authorization);
        return ApiResult.ok(service.mine(user.userId()).stream()
                .map(item -> service.toStorefront(item, user.credit()))
                .toList());
    }

    @GetMapping("/{id}")
    public ApiResult<ProductService.StorefrontProduct> find(@PathVariable long id) {
        return ApiResult.ok(service.toStorefront(service.find(id)));
    }

    @PostMapping
    public ApiResult<ProductService.StorefrontProduct> publish(
            @Valid @RequestBody PublishRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        CatalogUser user = authClient.requireUser(authorization);
        ProductService.ProductView created = service.publish(toPublish(request), user);
        return ApiResult.ok(service.toStorefront(created, user.credit()));
    }

    @PutMapping("/{id}")
    public ApiResult<ProductService.StorefrontProduct> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateProductRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        CatalogUser user = authClient.requireUser(authorization);
        ProductService.ProductView updated = service.update(id, new ProductService.UpdateProduct(
                request.name(), request.category(), request.description(), request.condition(),
                request.story(), request.price(), request.floorPrice(), request.location(), request.image()), user);
        return ApiResult.ok(service.toStorefront(updated, user.credit()));
    }

    private static ProductService.PublishRequest toPublish(PublishRequest request) {
        return new ProductService.PublishRequest(
                request.scene(), request.title(), request.image(), request.category(), request.price(),
                request.condition(), request.description(), request.story(), request.floorPrice(), request.location());
    }

    public record PublishRequest(
            String scene,
            @NotBlank(message = "标题不能为空") String title,
            @NotBlank(message = "图片不能为空") String image,
            @NotBlank(message = "分类不能为空") String category,
            @NotNull(message = "价格不能为空") @DecimalMin("0.01") BigDecimal price,
            String condition,
            @NotBlank(message = "描述不能为空") String description,
            String story,
            BigDecimal floorPrice,
            String location) {}

    public record UpdateProductRequest(
            @JsonAlias("title") @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 100) String category,
            @Size(max = 2000) String description,
            @Size(max = 100) String condition,
            @Size(max = 2000) String story,
            @DecimalMin("0.01") BigDecimal price,
            @DecimalMin("0.00") BigDecimal floorPrice,
            @Size(max = 255) String location,
            @Size(max = 1000) String image) {}
}
