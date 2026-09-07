package com.example.mall.identity.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.mall.identity.auth.dto.AuthUserView;
import com.example.mall.identity.auth.dto.LoginRequest;
import com.example.mall.identity.auth.dto.LoginResponse;
import com.example.mall.identity.auth.dto.ProfileUpdateRequest;
import com.example.mall.identity.auth.dto.RegisterRequest;
import com.example.mall.identity.auth.mapper.UserMapper;
import com.example.mall.identity.auth.model.StoredUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {

    private UserMapper userMapper;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        when(userMapper.countUserColumn(anyString())).thenReturn(1);
        authService = new AuthService(userMapper);
    }

    @Test
    // 测试登录成功的情况
    void loginReturnsTokenAndMasksPhone() {
        StoredUser user = user(
                "alice",
                new BCryptPasswordEncoder().encode("secret123"),
                "13800138000",
                "buyer",
                "normal");
        when(userMapper.findByUsername("alice")).thenReturn(user);

        LoginResponse response = authService.login(login("alice", "secret123"));

        assertNotNull(response.getToken());
        assertFalse(response.getToken().isBlank());
        assertEquals("alice", response.getUser().getUsername());
        assertEquals("138****8000", response.getUser().getPhoneMasked());
        assertEquals("buyer", response.getUser().getRole());
    }

    @Test
    // 测试登录失败的情况：密码错误
    void loginRejectsWrongPassword() {
        StoredUser user = user(
                "alice",
                new BCryptPasswordEncoder().encode("secret123"),
                "13800138000",
                "buyer",
                "normal");
        when(userMapper.findByUsername("alice")).thenReturn(user);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(login("alice", "wrong-password")));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    // 测试登录失败的情况：账户被禁用
    void loginRejectsDisabledAccount() {
        StoredUser user = user(
                "alice",
                new BCryptPasswordEncoder().encode("secret123"),
                "13800138000",
                "buyer",
                "disabled");
        when(userMapper.findByUsername("alice")).thenReturn(user);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(login("alice", "secret123")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    // 测试登录失败的情况：用户名不存在
    void loginRejectsUnknownUser() {
        when(userMapper.findByUsername("unknown")).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(login("unknown", "pass123"))
        );
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    // 测试注册失败的情况：尝试注册为管理员
    void registerRejectsAdminRole() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new-admin");
        request.setPassword("secret123");
        request.setPhone("13900139000");
        request.setRole("admin");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    private static LoginRequest login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private static StoredUser user(
            String username,
            String passwordHash,
            String phone,
            String role,
            String status) {
        StoredUser user = new StoredUser(username, passwordHash, phone, role);
        user.setUserId(101);
        user.setCredit(100);
        user.setStatus(status);
        return user;
    }

    @Test
    // 测试注册失败的情况：尝试注册一个已存在的用户名
    void registerRejectsDuplicateUsername() {
        // 模拟：数据库里已经有一个叫 "alice" 的用户
        when(userMapper.findByUsername("alice")).thenReturn(
            new StoredUser("alice", "hash", "13800138000", "buyer")
        );

        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("secret123");
        request.setPhone("13900139000");
        request.setRole("buyer");

        // 期望：注册时抛出“用户名已存在”的异常
        ResponseStatusException error = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(request)
        );
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    // 注册失败：手机号已被注册
    void registerRejectsDuplicatePhone() {
        // 模拟：数据库里已经有一个手机号为 "13800138000" 的用户
        when(userMapper.findByPhone("13800138000")).thenReturn(
            new StoredUser("alice", "hash", "13800138000", "buyer")
        );

        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("secret123");
        request.setPhone("13800138000");
        request.setRole("buyer");

        ResponseStatusException error = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(request)
        );
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    // 注册失败：必填字段为空
    void registerRejectsEmptyFields() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword("");
        request.setPhone("");
        request.setRole("");

        // 预期：参数校验层抛出 BAD_REQUEST（状态码 400）
        ResponseStatusException error = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    // 测试注册成功的情况：创建一个新用户
    void registerSuccessCreatesUser() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("secret123");
        request.setPhone("13900139000");
        request.setRole("buyer");

        when(userMapper.findByUsername("newuser")).thenReturn(null);
        when(userMapper.insertUser(any(StoredUser.class))).thenReturn(1);

        assertDoesNotThrow(() -> authService.register(request));
    }

    @Test
    // 测试获取当前用户信息的情况：使用有效的 token
    void meReturnsCurrentUserForValidToken() {
        StoredUser user = user(
                "alice",
                new BCryptPasswordEncoder().encode("secret123"),
                "13800138000",
                "seller",
                "normal");
        when(userMapper.findByUsername("alice")).thenReturn(user);

        LoginResponse loginResponse = authService.login(login("alice", "secret123"));
        AuthUserView currentUser = authService.me(loginResponse.getToken());

        assertEquals("alice", currentUser.getUsername());
        assertEquals("seller", currentUser.getRole());
        assertEquals("138****8000", currentUser.getPhoneMasked());
    }

    @Test
    // 测试获取当前用户信息的情况：缺少 token
    void meRejectsMissingToken() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.me(null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    // 测试获取当前用户信息的情况：使用无效的 token
    void meRejectsUnknownToken() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.me("not-real-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    // 测试更新用户资料的情况：更新头像 URL 为有效的路径
    void updateProfileAcceptsValidAvatarUrl() {
        StoredUser user = user(
                "alice",
                new BCryptPasswordEncoder().encode("secret123"),
                "13800138000",
                "buyer",
                "normal");
        when(userMapper.findByUsername("alice")).thenReturn(user);
        LoginResponse loginResponse = authService.login(login("alice", "secret123"));

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setAvatarUrl("/files/avatar.png");

        AuthUserView updated = authService.updateProfile(loginResponse.getToken(), request);

        assertEquals("/files/avatar.png", updated.getAvatarUrl());
        assertEquals("alice", updated.getUsername());
    }

    @Test
    // 测试更新用户资料的情况：尝试更新头像 URL 为无效的路径
    void updateProfileRejectsInvalidAvatarUrl() {
        StoredUser user = user(
                "alice",
                new BCryptPasswordEncoder().encode("secret123"),
                "13800138000",
                "buyer",
                "normal");
        when(userMapper.findByUsername("alice")).thenReturn(user);
        LoginResponse loginResponse = authService.login(login("alice", "secret123"));

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setAvatarUrl("ftp://example.com/avatar.png");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.updateProfile(loginResponse.getToken(), request));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    // 测试更新用户资料的情况：尝试更新头像 URL 为过长的路径
    void updateProfileRejectsTooLongAvatarUrl() {
        StoredUser user = user(
                "alice",
                new BCryptPasswordEncoder().encode("secret123"),
                "13800138000",
                "buyer",
                "normal");
        when(userMapper.findByUsername("alice")).thenReturn(user);
        LoginResponse loginResponse = authService.login(login("alice", "secret123"));

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setAvatarUrl("https://example.com/" + "a".repeat(500));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.updateProfile(loginResponse.getToken(), request));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    // 测试搜索用户的情况：使用空白关键字
    void searchUsersReturnsEmptyForBlankKeyword() {
        assertTrue(authService.searchUsers("   ").isEmpty());
    }

    @Test
    // 测试搜索用户的情况：使用有效关键字返回匹配结果
    void searchUsersReturnsResultsForKeyword() {
        StoredUser user = user(
                "alice",
                new BCryptPasswordEncoder().encode("secret123"),
                "13800138000",
                "buyer",
                "normal");
        when(userMapper.searchUsersByKeyword("ali")).thenReturn(List.of(user));

        List<AuthUserView> results = authService.searchUsers("ali");

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals("alice", results.get(0).getUsername());
    }

    @Test
    // 测试获取用户信息的情况：使用有效的 userId
    void getUserByIdReturnsUserInfo() {
        StoredUser user = user(
                "alice",
                new BCryptPasswordEncoder().encode("secret123"),
                "13800138000",
                "buyer",
                "normal");
        when(userMapper.findById(101)).thenReturn(user);

        AuthUserView view = authService.getUserById(101);

        assertEquals("alice", view.getUsername());
        assertEquals("138****8000", view.getPhoneMasked());
    }

    @Test
    void getUserByIdRejectsMissingUser() {
        when(userMapper.findById(999)).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.getUserById(999));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }
}


