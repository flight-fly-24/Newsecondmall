package com.example.mall.order;

public interface UserClient {
    CurrentUser requireLogin(String authorization);

    void requireActiveUser(long userId);

    void requireActiveUserAndAddress(long userId, long addressId);

    record CurrentUser(long userId, String status) {
        boolean isActive() {
            return status != null && "normal".equalsIgnoreCase(status);
        }
    }
}
