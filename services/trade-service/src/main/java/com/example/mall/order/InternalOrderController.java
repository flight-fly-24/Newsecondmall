package com.example.mall.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalOrderController {
    private final OrderService service;

    public InternalOrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/internal/orders/{orderId}")
    public OrderService.OrderView find(@PathVariable long orderId) {
        return service.find(orderId);
    }

    @GetMapping("/internal/orders/{orderId}/participants")
    public OrderService.OrderParticipants participants(@PathVariable long orderId) {
        return service.participants(orderId);
    }

    @GetMapping("/internal/sellers/{sellerId}/summary")
    public OrderService.SellerSummary sellerSummary(@PathVariable long sellerId) {
        return service.sellerSummary(sellerId);
    }
}
