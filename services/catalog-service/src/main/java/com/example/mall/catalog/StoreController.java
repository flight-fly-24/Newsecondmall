package com.example.mall.catalog;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stores")
public class StoreController {
    private final StoreService service;
    private final ProductService products;
    private final HttpAuthClient authClient;

    public StoreController(StoreService service, ProductService products, HttpAuthClient authClient) {
        this.service = service;
        this.products = products;
        this.authClient = authClient;
    }

    @GetMapping public ApiResult<List<StoreService.StoreView>> list() { return ApiResult.ok(service.list()); }

    @GetMapping("/mine")
    public ApiResult<StoreService.StoreView> mine(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(service.mine(requireUserId(userId, authorization)));
    }

    @GetMapping("/{id}")
    public ApiResult<StoreService.StoreView> find(@PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(authorization == null ? service.find(id)
                : service.find(id, authClient.requireUser(authorization).userId()));
    }

    @GetMapping("/{id}/products")
    public ApiResult<List<ProductService.StorefrontProduct>> storeProducts(@PathVariable long id) {
        return ApiResult.ok(service.products(id).stream().map(products::toStorefront).toList());
    }

    @PostMapping("/{id}/follow")
    public ApiResult<StoreService.StoreView> follow(@PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(service.follow(id, authClient.requireUser(authorization).userId()));
    }

    @DeleteMapping("/{id}/follow")
    public ApiResult<StoreService.StoreView> unfollow(@PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(service.unfollow(id, authClient.requireUser(authorization).userId()));
    }

    private long requireUserId(Long userId, String authorization) {
        return userId != null ? CatalogUser.required(userId, null, null).userId()
                : authClient.requireUser(authorization).userId();
    }
}
