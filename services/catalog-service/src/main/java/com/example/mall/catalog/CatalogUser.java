package com.example.mall.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record CatalogUser(long userId, String role, String username, int credit) {
    public CatalogUser(long userId, String role, String username) {
        this(userId, role, username, 100);
    }

    public static CatalogUser required(Long userId, String role, String username) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authenticated user is required");
        }
        return new CatalogUser(userId, role == null ? "" : role.trim().toLowerCase(),
                username == null ? "" : username.trim(), 100);
    }

    public boolean isSeller() { return "seller".equalsIgnoreCase(role); }
    public boolean isAdmin() { return "admin".equalsIgnoreCase(role); }
}
