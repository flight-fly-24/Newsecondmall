package com.example.mall.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalUserController {
    private final UserService service;

    public InternalUserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/internal/users/{id}")
    public UserService.UserView find(@PathVariable long id) {
        return service.find(id);
    }

    @GetMapping("/internal/users/{userId}/addresses/{addressId}")
    public UserService.AddressView address(@PathVariable long userId, @PathVariable long addressId) {
        return service.findAddress(userId, addressId);
    }
}
