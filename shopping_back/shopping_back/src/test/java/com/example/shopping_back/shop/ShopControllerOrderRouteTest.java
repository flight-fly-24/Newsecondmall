package com.example.shopping_back.shop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class ShopControllerOrderRouteTest {

    @Test
    void exposesOrderRoutesRequiredByApiRegressionSuite() {
        assertTrue(hasGetRoute("/orders"));
        assertTrue(hasPostRoute("/orders"));
        assertTrue(hasPostRoute("/orders/{id}/cancel"));
        assertTrue(hasPostRoute("/orders/{id}/review"));
    }

    private boolean hasGetRoute(String path) {
        return Arrays.stream(ShopController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .anyMatch(path::equals);
    }

    private boolean hasPostRoute(String path) {
        return Arrays.stream(ShopController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .anyMatch(path::equals);
    }
}
