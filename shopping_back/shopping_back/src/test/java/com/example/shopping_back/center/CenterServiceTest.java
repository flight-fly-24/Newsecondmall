package com.example.shopping_back.center;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.example.shopping_back.auth.AuthService;
import com.example.shopping_back.auth.dto.AuthUserView;
import com.example.shopping_back.center.CenterDtos.AdminCenter;
import com.example.shopping_back.center.CenterDtos.BuyerCenter;
import com.example.shopping_back.center.CenterDtos.InteractionCreateRequest;
import com.example.shopping_back.center.CenterDtos.InteractionItem;
import com.example.shopping_back.center.CenterDtos.ModuleCard;
import com.example.shopping_back.center.CenterDtos.RealNameView;
import com.example.shopping_back.center.CenterDtos.RejectRequest;
import com.example.shopping_back.center.CenterDtos.SellerCenter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CenterServiceTest {

    private CenterMapper mapper;
    private AuthService authService;
    private CenterService centerService;

    @BeforeEach
    void setUp() {
        mapper = mock(CenterMapper.class);
        authService = mock(AuthService.class);
        when(mapper.countColumn(anyString(), anyString())).thenReturn(1);
        when(authService.me("token")).thenReturn(user());
        when(mapper.creditRecordCount(7)).thenReturn(1);
        when(mapper.favoriteCount(7)).thenReturn(0);
        when(mapper.browseCount(7)).thenReturn(0);
        when(mapper.followCount(7)).thenReturn(0);
        when(mapper.followTopicCount(7)).thenReturn(0);
        centerService = new CenterService(mapper, authService);
    }

    @Test
    void addFavoriteStoresActualGoodsId() {
        centerService.addBuyerItem("token", "favorite", new InteractionCreateRequest("42", "显示器", "松果小店"));

        verify(mapper).addFavorite(7, 42, "显示器");
    }

    @Test
    void addBrowseStoresActualGoodsId() {
        centerService.addBuyerItem("token", "history", new InteractionCreateRequest("18", "二手相机", "松果小店"));

        verify(mapper).addBrowse(7, 18, "二手相机");
    }

    @Test
    void addFavoriteRejectsMissingGoodsId() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> centerService.addBuyerItem("token", "favorite", new InteractionCreateRequest("", "显示器", "松果小店")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    private static AuthUserView user() {
        return new AuthUserView(7, "alice", "138****8000", 100, "buyer", "买家", false, "normal", "");
    }

    // ==================== 用户中心模块测试（TC-USER-01 ~ 03, 10） ====================

    @Test
    // TC-USER-01 买家中心
    void buyerCenterReturnsDashboard() {
        // 1. 准备 Mock 数据
        when(mapper.favoriteCount(7)).thenReturn(5);
        when(mapper.browseCount(7)).thenReturn(3);
        when(mapper.followCount(7)).thenReturn(2);
        when(mapper.followTopicCount(7)).thenReturn(4);
        when(mapper.latestRealName(7)).thenReturn(null);
        when(mapper.latestCreditReason(7)).thenReturn("信用良好");

        // 2. 执行方法
        BuyerCenter result = centerService.buyer("token");

        // 3. 断言
        assertNotNull(result);
        assertEquals("alice", result.username());
        assertEquals("138****8000", result.phoneMasked());
        assertEquals("正常", result.accountStatus());
        assertNotNull(result.modules());
        assertTrue(result.modules().size() >= 4);

        // 验证信用分卡片存在
        boolean hasCreditCard = result.modules().stream()
                .anyMatch(card -> "信用分".equals(card.title()));
        assertTrue(hasCreditCard, "买家中心应包含信用分卡片");
    }

    @Test
    // TC-USER-02 卖家中心/卖家工作台
    void sellerCenterReturnsDashboard() {
        // 1. 准备 Mock 数据（用户角色为 seller）
        AuthUserView sellerUser = new AuthUserView(7, "alice", "138****8000", 100, "seller", "卖家", false, "normal", "");
        when(authService.me("token")).thenReturn(sellerUser);

        when(mapper.storeName(7)).thenReturn("Alice 的店铺");
        when(mapper.storeStatus(7)).thenReturn("normal");
        when(mapper.sellerGoodsCount(7, "0")).thenReturn(10);
        when(mapper.sellerGoodsCount(7, "2")).thenReturn(2);
        when(mapper.sellerOrderCount(7, "pending_ship")).thenReturn(3);
        when(mapper.sellerAmount(7)).thenReturn(BigDecimal.valueOf(1500));
        when(mapper.violationCount(7)).thenReturn(0);
        when(mapper.latestRealName(7)).thenReturn(null);

        // 2. 执行方法
        SellerCenter result = centerService.seller("token");

        // 3. 断言
        assertNotNull(result);
        assertEquals("alice", result.username());
        assertEquals("Alice 的店铺", result.storeName());
        assertEquals("正常", result.storeStatus());
        assertNotNull(result.modules());
        assertTrue(result.modules().size() >= 4);

        // 验证店铺信息卡片存在
        boolean hasStoreCard = result.modules().stream()
                .anyMatch(card -> "店铺信息".equals(card.title()));
        assertTrue(hasStoreCard, "卖家中心应包含店铺信息卡片");
    }

    @Test
    // TC-USER-03 管理员中心/管理后台
    void adminCenterReturnsDashboard() {
        // 1. 准备 Mock 数据（用户角色为 admin）
        AuthUserView adminUser = new AuthUserView(3, "admin", "139****0000", 100, "admin", "管理员", false, "normal", "");
        when(authService.me("token")).thenReturn(adminUser);

        when(mapper.totalUsers()).thenReturn(50);
        when(mapper.roleCount("buyer")).thenReturn(30);
        when(mapper.roleCount("seller")).thenReturn(15);
        when(mapper.disabledUsers()).thenReturn(2);
        when(mapper.totalGoods()).thenReturn(100);
        when(mapper.pendingGoods()).thenReturn(5);
        when(mapper.pendingStores()).thenReturn(2);
        when(mapper.pendingRealName()).thenReturn(3);
        when(mapper.users()).thenReturn(List.of());
        when(mapper.pendingRealNames()).thenReturn(List.of());

        // 2. 执行方法
        AdminCenter result = centerService.admin("token");

        // 3. 断言
        assertNotNull(result);
        assertEquals("admin", result.username());
        assertNotNull(result.modules());
        assertTrue(result.modules().size() >= 4);

        // 验证平台总览卡片存在
        boolean hasOverviewCard = result.modules().stream()
                .anyMatch(card -> "平台总览".equals(card.title()));
        assertTrue(hasOverviewCard, "管理员中心应包含平台总览卡片");

        // 验证用户列表和实名认证审核列表不为 null
        assertNotNull(result.users());
        assertNotNull(result.realNameAudits());
    }

    @Test
    // TC-USER-10 信用分查看（通过 buyer 中心获取）
    void buyerCenterContainsCreditScore() {
        // 1. 准备 Mock 数据（用户信用分已在 setUp 中 mock 为 100）
        when(mapper.favoriteCount(7)).thenReturn(0);
        when(mapper.browseCount(7)).thenReturn(0);
        when(mapper.followCount(7)).thenReturn(0);
        when(mapper.followTopicCount(7)).thenReturn(0);
        when(mapper.latestRealName(7)).thenReturn(null);
        when(mapper.latestCreditReason(7)).thenReturn("系统初始化信用档案");

        // 2. 执行方法
        BuyerCenter result = centerService.buyer("token");

        // 3. 断言
        assertNotNull(result);

        // 查找信用分卡片
        ModuleCard creditCard = result.modules().stream()
                .filter(card -> "信用分".equals(card.title()))
                .findFirst()
                .orElse(null);

        assertNotNull(creditCard, "买家中心应包含信用分卡片");
        assertEquals("100", creditCard.value(), "信用分应为 100");
        assertEquals("优秀", creditCard.status(), "信用等级应为 优秀");
    }
    // ==================== 管理审核模块补充测试（TC-ADMIN-05） ====================

    @Test
    // TC-ADMIN-05 实名认证审核 - 审核通过
    void approveRealNameSuccess() {
        // 1. 准备管理员用户
        AuthUserView adminUser = new AuthUserView(3, "admin", "139****0000", 100, "admin", "管理员", false, "normal", "");
        when(authService.me("admin-token")).thenReturn(adminUser);

        // 2. Mock Mapper 行为：审核通过
        when(mapper.approveRealName(1, 3)).thenReturn(1);
        
        // 3. Mock admin() 依赖的统计数据（避免 NPE）
        when(mapper.totalUsers()).thenReturn(10);
        when(mapper.roleCount("buyer")).thenReturn(5);
        when(mapper.roleCount("seller")).thenReturn(3);
        when(mapper.disabledUsers()).thenReturn(0);
        when(mapper.totalGoods()).thenReturn(20);
        when(mapper.pendingGoods()).thenReturn(0);
        when(mapper.pendingStores()).thenReturn(0);
        when(mapper.pendingRealName()).thenReturn(1);
        when(mapper.users()).thenReturn(List.of());
        when(mapper.pendingRealNames()).thenReturn(List.of());

        // 4. 执行方法
        AdminCenter result = centerService.approveRealName("admin-token", 1);

        // 5. 断言
        assertNotNull(result);
        assertEquals("admin", result.username());
        verify(mapper, times(1)).approveRealName(1, 3);
    }

    @Test
    // TC-ADMIN-05 实名认证审核 - 审核拒绝
    void rejectRealNameSuccess() {
        // 1. 准备管理员用户
        AuthUserView adminUser = new AuthUserView(3, "admin", "139****0000", 100, "admin", "管理员", false, "normal", "");
        when(authService.me("admin-token")).thenReturn(adminUser);

        // 2. 准备拒绝请求
        RejectRequest rejectRequest = new RejectRequest("身份证照片不清晰");

        // 3. Mock Mapper 行为：审核拒绝
        when(mapper.rejectRealName(1, 3, "身份证照片不清晰")).thenReturn(1);
        
        // 4. Mock admin() 依赖的统计数据
        when(mapper.totalUsers()).thenReturn(10);
        when(mapper.roleCount("buyer")).thenReturn(5);
        when(mapper.roleCount("seller")).thenReturn(3);
        when(mapper.disabledUsers()).thenReturn(0);
        when(mapper.totalGoods()).thenReturn(20);
        when(mapper.pendingGoods()).thenReturn(0);
        when(mapper.pendingStores()).thenReturn(0);
        when(mapper.pendingRealName()).thenReturn(1);
        when(mapper.users()).thenReturn(List.of());
        when(mapper.pendingRealNames()).thenReturn(List.of());

        // 5. 执行方法
        AdminCenter result = centerService.rejectRealName("admin-token", 1, rejectRequest);

        // 6. 断言
        assertNotNull(result);
        assertEquals("admin", result.username());
        verify(mapper, times(1)).rejectRealName(1, 3, "身份证照片不清晰");
    }

        // ==================== 补充 CenterService 覆盖率测试 ====================

    @Test
    // 提交实名认证（买家）
    void submitRealNameSuccess() {
        when(authService.me("token")).thenReturn(user());
        when(mapper.deleteRealNameByUserId(7)).thenReturn(1);
        when(mapper.insertRealName(7, "张三", "1301**********1234")).thenReturn(1);
        when(mapper.favoriteCount(7)).thenReturn(0);
        when(mapper.browseCount(7)).thenReturn(0);
        when(mapper.followCount(7)).thenReturn(0);
        when(mapper.followTopicCount(7)).thenReturn(0);
        when(mapper.latestRealName(7)).thenReturn(null);
        when(mapper.latestCreditReason(7)).thenReturn("系统初始化信用档案");

        CenterDtos.RealNameSubmitRequest request = new CenterDtos.RealNameSubmitRequest("张三", "13012345678901234");
        BuyerCenter result = centerService.submitRealName("token", request);

        assertNotNull(result);
        assertEquals("alice", result.username());
        verify(mapper, times(1)).insertRealName(7, "张三", "1301**********1234");
    }

    @Test
    // 提交实名认证 - 请求为空
    void submitRealNameRejectsNullRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> centerService.submitRealName("token", null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    // 提交实名认证 - 姓名为空
    void submitRealNameRejectsEmptyRealName() {
        CenterDtos.RealNameSubmitRequest request = new CenterDtos.RealNameSubmitRequest("", "13012345678901234");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> centerService.submitRealName("token", request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    // 取消实名认证
    void cancelRealNameSuccess() {
        when(authService.me("token")).thenReturn(user());
        when(mapper.deleteRealNameByUserId(7)).thenReturn(1);
        when(mapper.favoriteCount(7)).thenReturn(0);
        when(mapper.browseCount(7)).thenReturn(0);
        when(mapper.followCount(7)).thenReturn(0);
        when(mapper.followTopicCount(7)).thenReturn(0);
        when(mapper.latestRealName(7)).thenReturn(null);
        when(mapper.latestCreditReason(7)).thenReturn("系统初始化信用档案");

        BuyerCenter result = centerService.cancelRealName("token");

        assertNotNull(result);
        verify(mapper, times(1)).deleteRealNameByUserId(7);
    }

    @Test
    // buyerItems - 收藏列表
    void buyerItemsReturnsFavorites() {
        when(authService.me("token")).thenReturn(user());
        List<InteractionItem> items = List.of(
                new InteractionItem(1, "测试商品", "收藏商品", "favorite", "2026-08-28 10:00", 101)
        );
        when(mapper.favorites(7)).thenReturn(items);

        List<InteractionItem> result = centerService.buyerItems("token", "favorite");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("测试商品", result.get(0).title());
        verify(mapper, times(1)).favorites(7);
    }

    @Test
    // buyerItems - 浏览历史
    void buyerItemsReturnsHistory() {
        when(authService.me("token")).thenReturn(user());
        List<InteractionItem> items = List.of(
                new InteractionItem(1, "浏览商品", "浏览足迹", "history", "2026-08-28 10:00", 101)
        );
        when(mapper.browseHistory(7)).thenReturn(items);

        List<InteractionItem> result = centerService.buyerItems("token", "history");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(mapper, times(1)).browseHistory(7);
    }

    @Test
    // buyerItems - 关注店铺
    void buyerItemsReturnsFollows() {
        when(authService.me("token")).thenReturn(user());
        List<InteractionItem> items = List.of(
                new InteractionItem(1, "测试店铺", "关注店铺", "follow", "2026-08-28 10:00", 1)
        );
        when(mapper.follows(7)).thenReturn(items);

        List<InteractionItem> result = centerService.buyerItems("token", "follow");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(mapper, times(1)).follows(7);
    }

    @Test
    // buyerItems - 关注话题
    void buyerItemsReturnsTopicFollows() {
        when(authService.me("token")).thenReturn(user());
        List<InteractionItem> items = List.of(
                new InteractionItem(1, "测试话题", "关注话题", "topicFollow", "2026-08-28 10:00", 1)
        );
        when(mapper.topicFollows(7)).thenReturn(items);

        List<InteractionItem> result = centerService.buyerItems("token", "topicFollow");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(mapper, times(1)).topicFollows(7);
    }

    @Test
    // buyerItems - 信用记录（默认类型）
    void buyerItemsReturnsCreditRecords() {
        when(authService.me("token")).thenReturn(user());
        List<InteractionItem> items = List.of(
                new InteractionItem(1, "系统初始化信用档案", "变动 0 分", "credit", "2026-08-28 10:00", null)
        );
        when(mapper.creditRecords(7)).thenReturn(items);

        List<InteractionItem> result = centerService.buyerItems("token", "unknown");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("credit", result.get(0).type());
        verify(mapper, times(1)).creditRecords(7);
    }

    @Test
    // clearBuyerItems - 清空收藏
    void clearBuyerItemsFavorites() {
        when(authService.me("token")).thenReturn(user());
        when(mapper.clearFavorites(7)).thenReturn(1);
        when(mapper.favoriteCount(7)).thenReturn(0);
        when(mapper.browseCount(7)).thenReturn(0);
        when(mapper.followCount(7)).thenReturn(0);
        when(mapper.followTopicCount(7)).thenReturn(0);
        when(mapper.latestRealName(7)).thenReturn(null);
        when(mapper.latestCreditReason(7)).thenReturn("系统初始化信用档案");

        BuyerCenter result = centerService.clearBuyerItems("token", "favorite");

        assertNotNull(result);
        verify(mapper, times(1)).clearFavorites(7);
    }

    @Test
    // adjustUserCredit - 正常调整
    void adjustUserCreditSuccess() {
        AuthUserView adminUser = new AuthUserView(3, "admin", "139****0000", 100, "admin", "管理员", false, "normal", "");
        when(authService.me("admin-token")).thenReturn(adminUser);
        when(mapper.userRole(7)).thenReturn("buyer");
        when(mapper.updateCredit(7, 10)).thenReturn(1);
        when(mapper.insertCreditChange(7, 10, "信誉良好奖励")).thenReturn(1);
        // 返回 admin 面板需要的 mock
        when(mapper.totalUsers()).thenReturn(10);
        when(mapper.roleCount("buyer")).thenReturn(5);
        when(mapper.roleCount("seller")).thenReturn(3);
        when(mapper.disabledUsers()).thenReturn(0);
        when(mapper.totalGoods()).thenReturn(20);
        when(mapper.pendingGoods()).thenReturn(0);
        when(mapper.pendingStores()).thenReturn(0);
        when(mapper.pendingRealName()).thenReturn(1);
        when(mapper.users()).thenReturn(List.of());
        when(mapper.pendingRealNames()).thenReturn(List.of());

        CenterDtos.CreditAdjustRequest request = new CenterDtos.CreditAdjustRequest(10, "信誉良好奖励");
        AdminCenter result = centerService.adjustUserCredit("admin-token", 7, request);

        assertNotNull(result);
        verify(mapper, times(1)).updateCredit(7, 10);
        verify(mapper, times(1)).insertCreditChange(7, 10, "信誉良好奖励");
    }

    @Test
    // adjustUserCredit - change为0
    void adjustUserCreditRejectsZeroChange() {
        AuthUserView adminUser = new AuthUserView(3, "admin", "139****0000", 100, "admin", "管理员", false, "normal", "");
        when(authService.me("admin-token")).thenReturn(adminUser);
        when(mapper.userRole(7)).thenReturn("buyer");

        CenterDtos.CreditAdjustRequest request = new CenterDtos.CreditAdjustRequest(0, "无效调整");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> centerService.adjustUserCredit("admin-token", 7, request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    // adjustUserCredit - 越权（普通用户）
    void adjustUserCreditRejectsUnauthorized() {
        when(authService.me("buyer-token")).thenReturn(user());
        when(mapper.userRole(7)).thenReturn("buyer");

        CenterDtos.CreditAdjustRequest request = new CenterDtos.CreditAdjustRequest(10, "测试");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> centerService.adjustUserCredit("buyer-token", 7, request)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    // updateUserStatus - 正常更新
    void updateUserStatusSuccess() {
        AuthUserView adminUser = new AuthUserView(3, "admin", "139****0000", 100, "admin", "管理员", false, "normal", "");
        when(authService.me("admin-token")).thenReturn(adminUser);
        when(mapper.updateUserStatus(7, "disabled")).thenReturn(1);
        // 返回 admin 面板需要的 mock
        when(mapper.totalUsers()).thenReturn(10);
        when(mapper.roleCount("buyer")).thenReturn(5);
        when(mapper.roleCount("seller")).thenReturn(3);
        when(mapper.disabledUsers()).thenReturn(1);
        when(mapper.totalGoods()).thenReturn(20);
        when(mapper.pendingGoods()).thenReturn(0);
        when(mapper.pendingStores()).thenReturn(0);
        when(mapper.pendingRealName()).thenReturn(1);
        when(mapper.users()).thenReturn(List.of());
        when(mapper.pendingRealNames()).thenReturn(List.of());

        CenterDtos.StatusUpdateRequest request = new CenterDtos.StatusUpdateRequest("disabled");
        AdminCenter result = centerService.updateUserStatus("admin-token", 7, request);

        assertNotNull(result);
        verify(mapper, times(1)).updateUserStatus(7, "disabled");
    }

    @Test
    // updateUserStatus - 非法状态
    void updateUserStatusRejectsInvalidStatus() {
        AuthUserView adminUser = new AuthUserView(3, "admin", "139****0000", 100, "admin", "管理员", false, "normal", "");
        when(authService.me("admin-token")).thenReturn(adminUser);

        CenterDtos.StatusUpdateRequest request = new CenterDtos.StatusUpdateRequest("invalid");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> centerService.updateUserStatus("admin-token", 7, request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    // deleteUser - 正常删除
    void deleteUserSuccess() {
        AuthUserView adminUser = new AuthUserView(3, "admin", "139****0000", 100, "admin", "管理员", false, "normal", "");
        when(authService.me("admin-token")).thenReturn(adminUser);
        when(mapper.usernameById(7)).thenReturn("testuser");
        when(mapper.deleteUser(7)).thenReturn(1);
        // 返回 admin 面板需要的 mock
        when(mapper.totalUsers()).thenReturn(9);
        when(mapper.roleCount("buyer")).thenReturn(5);
        when(mapper.roleCount("seller")).thenReturn(3);
        when(mapper.disabledUsers()).thenReturn(0);
        when(mapper.totalGoods()).thenReturn(20);
        when(mapper.pendingGoods()).thenReturn(0);
        when(mapper.pendingStores()).thenReturn(0);
        when(mapper.pendingRealName()).thenReturn(1);
        when(mapper.users()).thenReturn(List.of());
        when(mapper.pendingRealNames()).thenReturn(List.of());

        AdminCenter result = centerService.deleteUser("admin-token", 7);

        assertNotNull(result);
        verify(mapper, times(1)).deleteUser(7);
    }

    @Test
    // deleteUser - 删除演示账号
    void deleteUserRejectsDemoAccount() {
        AuthUserView adminUser = new AuthUserView(3, "admin", "139****0000", 100, "admin", "管理员", false, "normal", "");
        when(authService.me("admin-token")).thenReturn(adminUser);
        when(mapper.usernameById(1)).thenReturn("demo");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> centerService.deleteUser("admin-token", 1)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    // submitSellerRealName - 卖家提交实名
    void submitSellerRealNameSuccess() {
        AuthUserView sellerUser = new AuthUserView(7, "alice", "138****8000", 100, "seller", "卖家", false, "normal", "");
        when(authService.me("seller-token")).thenReturn(sellerUser);
        when(mapper.storeName(7)).thenReturn("Alice 的店铺");
        when(mapper.storeStatus(7)).thenReturn("normal");
        when(mapper.sellerGoodsCount(7, "0")).thenReturn(10);
        when(mapper.sellerGoodsCount(7, "2")).thenReturn(2);
        when(mapper.sellerOrderCount(7, "pending_ship")).thenReturn(3);
        when(mapper.sellerAmount(7)).thenReturn(BigDecimal.valueOf(1500));
        when(mapper.violationCount(7)).thenReturn(0);
        when(mapper.latestRealName(7)).thenReturn(null);
        when(mapper.deleteRealNameByUserId(7)).thenReturn(1);
        when(mapper.insertRealName(7, "张三", "1301**********1234")).thenReturn(1);

        CenterDtos.RealNameSubmitRequest request = new CenterDtos.RealNameSubmitRequest("张三", "13012345678901234");
        SellerCenter result = centerService.submitSellerRealName("seller-token", request);

        assertNotNull(result);
        assertEquals("alice", result.username());
        verify(mapper, times(1)).insertRealName(7, "张三", "1301**********1234");
    }

    @Test
    // cancelSellerRealName - 卖家取消实名
    void cancelSellerRealNameSuccess() {
        AuthUserView sellerUser = new AuthUserView(7, "alice", "138****8000", 100, "seller", "卖家", false, "normal", "");
        when(authService.me("seller-token")).thenReturn(sellerUser);
        when(mapper.storeName(7)).thenReturn("Alice 的店铺");
        when(mapper.storeStatus(7)).thenReturn("normal");
        when(mapper.sellerGoodsCount(7, "0")).thenReturn(10);
        when(mapper.sellerGoodsCount(7, "2")).thenReturn(2);
        when(mapper.sellerOrderCount(7, "pending_ship")).thenReturn(3);
        when(mapper.sellerAmount(7)).thenReturn(BigDecimal.valueOf(1500));
        when(mapper.violationCount(7)).thenReturn(0);
        when(mapper.latestRealName(7)).thenReturn(null);
        when(mapper.deleteRealNameByUserId(7)).thenReturn(1);

        SellerCenter result = centerService.cancelSellerRealName("seller-token");

        assertNotNull(result);
        verify(mapper, times(1)).deleteRealNameByUserId(7);
    }

    @Test
    // realNameOrDefault - 有实名记录
    void realNameOrDefaultReturnsExistingRealName() {
        // 通过 buyer() 间接测试，因为 realNameOrDefault 是 private 方法
        when(authService.me("token")).thenReturn(user());
        when(mapper.creditRecordCount(7)).thenReturn(1);
        when(mapper.favoriteCount(7)).thenReturn(0);
        when(mapper.browseCount(7)).thenReturn(0);
        when(mapper.followCount(7)).thenReturn(0);
        when(mapper.followTopicCount(7)).thenReturn(0);
        
        RealNameView realName = new RealNameView(1, 7, "alice", "张*", "1301**********1234", "approved", "");
        when(mapper.latestRealName(7)).thenReturn(realName);
        when(mapper.latestCreditReason(7)).thenReturn("系统初始化信用档案");

        BuyerCenter result = centerService.buyer("token");

        assertNotNull(result);
        assertNotNull(result.realName());
        assertEquals("approved", result.realName().status());
    }
}
