package com.example.mall.order;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping
    public OrderController.ApiResult<List<CartService.CartItemView>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return OrderController.ApiResult.ok(service.list(authorization));
    }

    @PostMapping
    public OrderController.ApiResult<List<CartService.CartItemView>> add(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) CartService.AddCartRequest body) {
        return OrderController.ApiResult.ok(service.add(authorization, body));
    }

    @PutMapping("/{cartId}")
    public OrderController.ApiResult<List<CartService.CartItemView>> updateQuantity(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long cartId,
            @RequestBody(required = false) CartService.UpdateCartRequest body) {
        return OrderController.ApiResult.ok(service.updateQuantity(authorization, cartId, body));
    }

    @DeleteMapping("/{cartId}")
    public OrderController.ApiResult<List<CartService.CartItemView>> remove(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long cartId) {
        return OrderController.ApiResult.ok(service.remove(authorization, cartId));
    }

    @PutMapping("/{cartId}/select")
    public OrderController.ApiResult<List<CartService.CartItemView>> select(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long cartId,
            @RequestBody(required = false) CartService.SelectCartRequest body) {
        return OrderController.ApiResult.ok(service.select(authorization, cartId, body));
    }
}
