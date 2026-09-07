package com.example.mall.identity.auth.dto;

public class AuthUserView {

    private final Integer userId;
    private final String username;
    private final String phoneMasked;
    private final Integer credit;
    private final String role;
    private final String roleLabel;
    private final Boolean realNameVerified;
    private final String status;
    private final String avatarUrl;

    public AuthUserView(Integer userId, String username, String phoneMasked, Integer credit,
                        String role, String roleLabel, Boolean realNameVerified, String status, String avatarUrl) {
        this.userId = userId;
        this.username = username;
        this.phoneMasked = phoneMasked;
        this.credit = credit;
        this.role = role;
        this.roleLabel = roleLabel;
        this.realNameVerified = realNameVerified;
        this.status = status;
        this.avatarUrl = avatarUrl;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPhoneMasked() {
        return phoneMasked;
    }

    public Integer getCredit() {
        return credit;
    }

    public String getRole() {
        return role;
    }

    public String getRoleLabel() {
        return roleLabel;
    }

    public Boolean getRealNameVerified() {
        return realNameVerified;
    }

    public String getStatus() {
        return status;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }
}
