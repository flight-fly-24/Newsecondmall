package com.example.shopping_back.center;

import java.util.List;

public final class CenterDtos {
    private CenterDtos() {
    }

    public record ModuleCard(String title, String value, String desc, String status) {
    }

    public record BuyerCenter(
            String username,
            String phoneMasked,
            String accountStatus,
            RealNameView realName,
            List<ModuleCard> modules
    ) {
    }

    public record SellerCenter(
            String username,
            String phoneMasked,
            String storeName,
            String storeStatus,
            RealNameView realName,
            List<ModuleCard> modules
    ) {
    }

    public record AdminCenter(
            String username,
            String phoneMasked,
            List<ModuleCard> modules,
            List<UserRow> users,
            List<RealNameView> realNameAudits
    ) {
    }

    public record RealNameView(
            Integer id,
            Integer userId,
            String username,
            String realName,
            String idCardMasked,
            String status,
            String rejectReason
    ) {
    }

    public record UserRow(Integer userId, String username, String role, String status, Integer credit) {
    }

    public record InteractionItem(Integer id, String title, String desc, String type, String createdAt, Integer targetId) {
    }

    public record CreditAdjustRequest(Integer changeValue, String reason) {
    }

    public record InteractionCreateRequest(String itemId, String title, String storeName) {
    }

    public record RealNameSubmitRequest(String realName, String idCard) {
    }

    public record StatusUpdateRequest(String status) {
    }

    public record RejectRequest(String reason) {
    }
}
