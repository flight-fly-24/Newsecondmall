package com.example.shopping_back.center;

import com.example.shopping_back.auth.AuthService;
import com.example.shopping_back.auth.dto.AuthUserView;
import com.example.shopping_back.center.CenterDtos.AdminCenter;
import com.example.shopping_back.center.CenterDtos.BuyerCenter;
import com.example.shopping_back.center.CenterDtos.CreditAdjustRequest;
import com.example.shopping_back.center.CenterDtos.InteractionCreateRequest;
import com.example.shopping_back.center.CenterDtos.InteractionItem;
import com.example.shopping_back.center.CenterDtos.ModuleCard;
import com.example.shopping_back.center.CenterDtos.RealNameSubmitRequest;
import com.example.shopping_back.center.CenterDtos.RealNameView;
import com.example.shopping_back.center.CenterDtos.RejectRequest;
import com.example.shopping_back.center.CenterDtos.SellerCenter;
import com.example.shopping_back.center.CenterDtos.StatusUpdateRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CenterService {
    private final CenterMapper mapper;
    private final AuthService authService;

    public CenterService(CenterMapper mapper, AuthService authService) {
        this.mapper = mapper;
        this.authService = authService;
        ensureSchema();
    }

    public BuyerCenter buyer(String token) {
        AuthUserView user = current(token);
        ensureCreditRecord(user);
        RealNameView realName = realNameOrDefault(user);
        return new BuyerCenter(
                user.getUsername(),
                user.getPhoneMasked(),
                statusText(user.getStatus()),
                realName,
                List.of(
                        new ModuleCard("账号信息", user.getPhoneMasked(), "手机号脱敏、账号状态、登录身份", statusText(user.getStatus())),
                        new ModuleCard("信用分", String.valueOf(user.getCredit()), latestCredit(user), creditLevel(user.getCredit())),
                        new ModuleCard("我的互动", interactionValue(user), "收藏 / 足迹 / 关注店铺 / 关注话题均从数据库统计", "实时同步"),
                        new ModuleCard("实名认证", realNameStatusText(realName.status()), realNameDesc(realName), realName.status())
                ));
    }

    public SellerCenter seller(String token) {
        AuthUserView user = requireRole(token, "seller");
        ensureSellerStore(user);
        Integer violations = mapper.violationCount(user.getUserId());
        return new SellerCenter(
                user.getUsername(),
                user.getPhoneMasked(),
                mapper.storeName(user.getUserId()),
                statusText(mapper.storeStatus(user.getUserId())),
                realNameOrDefault(user),
                List.of(
                        new ModuleCard("店铺信息", mapper.storeName(user.getUserId()), "店铺状态、认证状态、店铺评分", statusText(mapper.storeStatus(user.getUserId()))),
                        new ModuleCard("商品概览", String.valueOf(mapper.sellerGoodsCount(user.getUserId(), "0")), "在售商品；待审核 " + mapper.sellerGoodsCount(user.getUserId(), "2") + " 件", "数据库 goods"),
                        new ModuleCard("订单概览", String.valueOf(mapper.sellerOrderCount(user.getUserId(), "pending_ship")), "待发货订单；累计成交 " + mapper.sellerAmount(user.getUserId()), "数据库 orders"),
                        new ModuleCard("店铺信用", String.valueOf(user.getCredit()), "违规次数 " + (violations == null ? 0 : violations), "经营信用")
                ));
    }

    public AdminCenter admin(String token) {
        AuthUserView user = requireRole(token, "admin");
        return new AdminCenter(
                user.getUsername(),
                user.getPhoneMasked(),
                List.of(
                        new ModuleCard("平台总览", String.valueOf(mapper.totalUsers()), "商品 " + mapper.totalGoods() + " 件，实名待审 " + mapper.pendingRealName() + " 条", "全站统计"),
                        new ModuleCard("用户管理", String.valueOf(mapper.roleCount("buyer") + mapper.roleCount("seller")), "冻结用户 " + mapper.disabledUsers() + " 个", "可冻结/解冻"),
                        new ModuleCard("实名认证审核", String.valueOf(mapper.pendingRealName()), "买家/卖家提交后进入这里", "待处理"),
                        new ModuleCard("商品店铺审核", String.valueOf(mapper.pendingGoods() + mapper.pendingStores()), "待审商品 " + mapper.pendingGoods() + "，待审店铺 " + mapper.pendingStores(), "风控审核")
                ),
                mapper.users(),
                mapper.pendingRealNames());
    }

    public BuyerCenter submitRealName(String token, RealNameSubmitRequest request) {
        AuthUserView user = current(token);
        if (request == null || isBlank(request.realName()) || isBlank(request.idCard())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写真实姓名和证件号");
        }
        mapper.deleteRealNameByUserId(user.getUserId());
        mapper.insertRealName(user.getUserId(), request.realName().trim(), maskIdCard(request.idCard().trim()));
        return buyer(token);
    }

    public BuyerCenter cancelRealName(String token) {
        AuthUserView user = current(token);
        mapper.deleteRealNameByUserId(user.getUserId());
        return buyer(token);
    }

    public List<InteractionItem> buyerItems(String token, String type) {
        AuthUserView user = current(token);
        return switch (normalizeInteractionType(type)) {
            case "favorite" -> mapper.favorites(user.getUserId());
            case "history" -> mapper.browseHistory(user.getUserId());
            case "follow" -> mapper.follows(user.getUserId());
            case "topicFollow" -> mapper.topicFollows(user.getUserId());
            default -> mapper.creditRecords(user.getUserId());
        };
    }

    public BuyerCenter addBuyerItem(String token, String type, InteractionCreateRequest request) {
        AuthUserView user = current(token);
        String normalized = normalizeInteractionType(type);
        String title = request == null || isBlank(request.title()) ? "未命名商品" : request.title().trim();
        String storeName = request == null || isBlank(request.storeName()) ? "未命名店铺" : request.storeName().trim();
        Integer goodsId = parsePositiveId(request == null ? null : request.itemId());
        if ("favorite".equals(normalized)) {
            if (goodsId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品信息缺失");
            }
            mapper.addFavorite(user.getUserId(), goodsId, title);
        } else if ("history".equals(normalized)) {
            if (goodsId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品信息缺失");
            }
            mapper.addBrowse(user.getUserId(), goodsId, title);
        } else if ("follow".equals(normalized)) {
            mapper.addFollow(user.getUserId(), storeName);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该类型不能从这里添加记录");
        }
        return buyer(token);
    }

    public BuyerCenter clearBuyerItems(String token, String type) {
        AuthUserView user = current(token);
        String normalized = normalizeInteractionType(type);
        if ("favorite".equals(normalized)) {
            mapper.clearFavorites(user.getUserId());
        } else if ("history".equals(normalized)) {
            mapper.clearBrowse(user.getUserId());
        } else if ("follow".equals(normalized)) {
            mapper.clearFollows(user.getUserId());
        } else if ("topicFollow".equals(normalized)) {
            mapper.clearTopicFollows(user.getUserId());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该类型不能清空");
        }
        return buyer(token);
    }

    public AdminCenter adjustUserCredit(String token, Integer userId, CreditAdjustRequest request) {
        requireRole(token, "admin");
        String role = mapper.userRole(userId);
        if (!"buyer".equals(role) && !"seller".equals(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only buyer and seller credit can be adjusted");
        }
        int change = request == null || request.changeValue() == null ? 0 : request.changeValue();
        if (change == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "信用分变动不能为 0");
        }
        String reason = request.reason() == null || request.reason().isBlank() ? "管理员调整信用分" : request.reason().trim();
        mapper.updateCredit(userId, change);
        mapper.insertCreditChange(userId, change, reason);
        return admin(token);
    }

    public SellerCenter submitSellerRealName(String token, RealNameSubmitRequest request) {
        AuthUserView user = requireRole(token, "seller");
        if (request == null || isBlank(request.realName()) || isBlank(request.idCard())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写真实姓名和证件号");
        }
        mapper.deleteRealNameByUserId(user.getUserId());
        mapper.insertRealName(user.getUserId(), request.realName().trim(), maskIdCard(request.idCard().trim()));
        return seller(token);
    }

    public SellerCenter cancelSellerRealName(String token) {
        AuthUserView user = requireRole(token, "seller");
        mapper.deleteRealNameByUserId(user.getUserId());
        return seller(token);
    }

    public AdminCenter updateUserStatus(String token, Integer userId, StatusUpdateRequest request) {
        requireRole(token, "admin");
        String status = request == null ? "" : request.status();
        if (!"normal".equals(status) && !"disabled".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "状态只能是 normal 或 disabled");
        }
        mapper.updateUserStatus(userId, status);
        return admin(token);
    }

    public AdminCenter deleteUser(String token, Integer userId) {
        requireRole(token, "admin");
        String username = mapper.usernameById(userId);
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if ("demo".equals(username) || "seller".equals(username) || "admin".equals(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "演示账号不能删除");
        }
        mapper.deleteRealNameByUserId(userId);
        mapper.clearFavorites(userId);
        mapper.clearBrowse(userId);
        mapper.clearFollows(userId);
        mapper.deleteCreditRecords(userId);
        mapper.deleteOrdersByUserId(userId);
        mapper.deleteStoresBySellerId(userId);
        mapper.deleteGoodsBySellerId(userId);
        mapper.deleteUser(userId);
        return admin(token);
    }

    public AdminCenter approveRealName(String token, Integer id) {
        AuthUserView admin = requireRole(token, "admin");
        mapper.approveRealName(id, admin.getUserId());
        return admin(token);
    }

    public AdminCenter rejectRealName(String token, Integer id, RejectRequest request) {
        AuthUserView admin = requireRole(token, "admin");
        String reason = request == null || isBlank(request.reason()) ? "信息不完整，请重新提交" : request.reason().trim();
        mapper.rejectRealName(id, admin.getUserId(), reason);
        return admin(token);
    }

    private AuthUserView current(String token) {
        return authService.me(token);
    }

    private AuthUserView requireRole(String token, String role) {
        AuthUserView user = current(token);
        if (!role.equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权限访问该角色页面");
        }
        return user;
    }

    private void ensureSchema() {
        mapper.createRealNameTable();
        mapper.createFavoriteTable();
        mapper.createBrowseTable();
        mapper.createFollowTable();
        mapper.createFollowTopicTable();
        mapper.createStoreTable();
        mapper.createOrdersTable();
        mapper.createCreditRecordTable();
        if (mapper.countColumn("favorite_goods", "item_title") == 0) {
            mapper.addFavoriteTitleColumn();
        }
        if (mapper.countColumn("browse_history", "item_title") == 0) {
            mapper.addBrowseTitleColumn();
        }
        if (mapper.countColumn("follow_store", "store_name") == 0) {
            mapper.addFollowStoreNameColumn();
        }
    }

    private void ensureCreditRecord(AuthUserView user) {
        if (mapper.creditRecordCount(user.getUserId()) == 0) {
            mapper.insertCreditRecord(user.getUserId(), "系统初始化信用档案");
        }
    }

    private void ensureSellerStore(AuthUserView user) {
        if (mapper.sellerStoreCount(user.getUserId()) == 0) {
            mapper.insertStore(user.getUserId(), user.getUsername() + " 的店铺");
        }
    }

    private RealNameView realNameOrDefault(AuthUserView user) {
        RealNameView realName = mapper.latestRealName(user.getUserId());
        if (realName != null) {
            return realName;
        }
        return new RealNameView(null, user.getUserId(), user.getUsername(), "", "", "none", "");
    }

    private String latestCredit(AuthUserView user) {
        String reason = mapper.latestCreditReason(user.getUserId());
        return reason == null ? "暂无信用变动记录" : reason;
    }

    private String interactionValue(AuthUserView user) {
        return mapper.favoriteCount(user.getUserId()) + " / " + mapper.browseCount(user.getUserId()) + " / " + mapper.followCount(user.getUserId()) + " / " + mapper.followTopicCount(user.getUserId());
    }

    private String creditLevel(Integer credit) {
        int score = credit == null ? 100 : credit;
        if (score >= 90) {
            return "优秀";
        }
        if (score >= 70) {
            return "良好";
        }
        return "需关注";
    }

    private String statusText(String status) {
        if ("disabled".equals(status)) {
            return "已冻结";
        }
        if ("pending".equals(status)) {
            return "待审核";
        }
        return "正常";
    }

    private String realNameStatusText(String status) {
        if ("approved".equals(status)) {
            return "已认证";
        }
        if ("pending".equals(status)) {
            return "待审核";
        }
        if ("rejected".equals(status)) {
            return "已驳回";
        }
        return "未认证";
    }

    private String realNameDesc(RealNameView realName) {
        if ("approved".equals(realName.status())) {
            return "管理员已审核通过";
        }
        if ("pending".equals(realName.status())) {
            return "已提交，等待管理员审核";
        }
        if ("rejected".equals(realName.status())) {
            return realName.rejectReason() == null ? "认证被驳回" : realName.rejectReason();
        }
        return "可提交实名模拟信息";
    }

    private String maskIdCard(String idCard) {
        if (idCard.length() <= 8) {
            return "****";
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Integer parsePositiveId(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeInteractionType(String type) {
        if ("favorite".equals(type) || "history".equals(type) || "follow".equals(type) || "topicFollow".equals(type) || "credit".equals(type)) {
            return type;
        }
        return "credit";
    }
}
