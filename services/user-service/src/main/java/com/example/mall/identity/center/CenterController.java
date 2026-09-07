package com.example.mall.identity.center;

import com.example.mall.identity.center.CenterDtos.AdminCenter;
import com.example.mall.identity.center.CenterDtos.BuyerCenter;
import com.example.mall.identity.center.CenterDtos.CreditAdjustRequest;
import com.example.mall.identity.center.CenterDtos.InteractionCreateRequest;
import com.example.mall.identity.center.CenterDtos.InteractionItem;
import com.example.mall.identity.center.CenterDtos.RealNameSubmitRequest;
import com.example.mall.identity.center.CenterDtos.RejectRequest;
import com.example.mall.identity.center.CenterDtos.SellerCenter;
import com.example.mall.identity.center.CenterDtos.StatusUpdateRequest;
import com.example.mall.common.dto.ApiResult;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/center")
public class CenterController {
    private final CenterService centerService;

    public CenterController(CenterService centerService) {
        this.centerService = centerService;
    }

    @GetMapping("/buyer")
    public ApiResult<BuyerCenter> buyer(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(centerService.buyer(bearerToken(authorization)));
    }

    @GetMapping("/seller")
    public ApiResult<SellerCenter> seller(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(centerService.seller(bearerToken(authorization)));
    }

    @GetMapping("/admin")
    public ApiResult<AdminCenter> admin(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(centerService.admin(bearerToken(authorization)));
    }

    @PostMapping("/buyer/realname")
    public ApiResult<BuyerCenter> submitRealName(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RealNameSubmitRequest body) {
        return ApiResult.ok(centerService.submitRealName(bearerToken(authorization), body));
    }

    @PutMapping("/buyer/realname/cancel")
    public ApiResult<BuyerCenter> cancelRealName(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(centerService.cancelRealName(bearerToken(authorization)));
    }

    @GetMapping("/buyer/items/{type}")
    public ApiResult<List<InteractionItem>> buyerItems(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("type") String type) {
        return ApiResult.ok(centerService.buyerItems(bearerToken(authorization), type));
    }

    @PostMapping("/buyer/items/{type}")
    public ApiResult<BuyerCenter> addBuyerItem(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("type") String type,
            @RequestBody(required = false) InteractionCreateRequest body) {
        return ApiResult.ok(centerService.addBuyerItem(bearerToken(authorization), type, body));
    }

    @PutMapping("/buyer/items/{type}/clear")
    public ApiResult<BuyerCenter> clearBuyerItems(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("type") String type) {
        return ApiResult.ok(centerService.clearBuyerItems(bearerToken(authorization), type));
    }

    @PostMapping("/seller/realname")
    public ApiResult<SellerCenter> submitSellerRealName(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RealNameSubmitRequest body) {
        return ApiResult.ok(centerService.submitSellerRealName(bearerToken(authorization), body));
    }

    @PutMapping("/seller/realname/cancel")
    public ApiResult<SellerCenter> cancelSellerRealName(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(centerService.cancelSellerRealName(bearerToken(authorization)));
    }

    @PutMapping("/admin/users/{userId}/status")
    public ApiResult<AdminCenter> updateUserStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("userId") Integer userId,
            @RequestBody StatusUpdateRequest body) {
        return ApiResult.ok(centerService.updateUserStatus(bearerToken(authorization), userId, body));
    }

    @PutMapping("/admin/users/{userId}/credit")
    public ApiResult<AdminCenter> adjustUserCredit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("userId") Integer userId,
            @RequestBody CreditAdjustRequest body) {
        return ApiResult.ok(centerService.adjustUserCredit(bearerToken(authorization), userId, body));
    }

    @DeleteMapping("/admin/users/{userId}")
    public ApiResult<AdminCenter> deleteUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("userId") Integer userId) {
        return ApiResult.ok(centerService.deleteUser(bearerToken(authorization), userId));
    }

    @PutMapping("/admin/realname/{id}/approve")
    public ApiResult<AdminCenter> approveRealName(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Integer id) {
        return ApiResult.ok(centerService.approveRealName(bearerToken(authorization), id));
    }

    @PutMapping("/admin/realname/{id}/reject")
    public ApiResult<AdminCenter> rejectRealName(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Integer id,
            @RequestBody(required = false) RejectRequest body) {
        return ApiResult.ok(centerService.rejectRealName(bearerToken(authorization), id, body));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String v = authorization.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return v.substring(7).trim();
        }
        return v;
    }
}
