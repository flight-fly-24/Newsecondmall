package com.example.mall.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class InternalProductController {
    private final ProductService service;

    public InternalProductController(ProductService service) { this.service = service; }

    @GetMapping("/internal/products/{id}/snapshot")
    public ProductService.ProductView snapshot(@PathVariable long id) { return service.find(id); }

    @PutMapping("/internal/products/{id}/sold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSold(@PathVariable long id, @RequestHeader("X-Order-Number") String orderNumber) {
        service.markSold(id, orderNumber);
    }
}
