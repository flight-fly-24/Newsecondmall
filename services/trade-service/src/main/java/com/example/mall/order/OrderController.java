package com.example.mall.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<List<OrderService.StorefrontOrderView>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String status) {
        return ApiResult.ok(service.listStorefront(authorization, status));
    }

    @PostMapping
    public ApiResult<List<OrderService.StorefrontOrderView>> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateOrdersRequest request) {
        return ApiResult.ok(service.createFromItems(authorization, request.items()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResult<OrderService.StorefrontOrderView> cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        return ApiResult.ok(service.cancel(authorization, id));
    }

    @PostMapping("/{id}/review")
    public ApiResult<OrderService.StorefrontOrderView> review(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @Valid @RequestBody OrderService.ReviewRequest request) {
        return ApiResult.ok(service.review(authorization, id, request));
    }

    @GetMapping("/{id}")
    public OrderService.OrderView find(@PathVariable long id) {
        return service.find(id);
    }

    public record CreateOrdersRequest(@NotEmpty List<OrderService.OrderItem> items) {}

    public record ApiResult<T>(int code, String message, T data) {
        public static <T> ApiResult<T> ok(T data) {
            return new ApiResult<>(0, "ok", data);
        }
    }
}
