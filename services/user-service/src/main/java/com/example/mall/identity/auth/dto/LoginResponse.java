package com.example.mall.identity.auth.dto;

public class LoginResponse {

    private final String token;
    private final AuthUserView user;

    public LoginResponse(String token, AuthUserView user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public AuthUserView getUser() {
        return user;
    }
}
