package com.example.mall.catalog;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/center/buyer/items")
public class ProductRelationController {
    private final ProductRelationService service;
    private final HttpAuthClient authClient;

    public ProductRelationController(ProductRelationService service, HttpAuthClient authClient) {
        this.service = service;
        this.authClient = authClient;
    }

    @GetMapping("/{type}")
    public ApiResult<List<ProductRelationService.RelationItem>> list(@PathVariable String type,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(service.list(type, requireUserId(userId, authorization)));
    }

    @PostMapping("/{type}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<ProductRelationService.RelationItem> add(@PathVariable String type,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AddRelationRequest request) {
        return ApiResult.ok(service.add(type, requireUserId(userId, authorization), request.goodsId()));
    }

    @PutMapping("/{type}/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable String type,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        service.clear(type, requireUserId(userId, authorization));
    }

    private long requireUserId(Long userId, String authorization) {
        return userId != null ? CatalogUser.required(userId, null, null).userId()
                : authClient.requireUser(authorization).userId();
    }

    public record AddRelationRequest(@JsonAlias("itemId") @NotNull @Positive Long goodsId) {}
}
