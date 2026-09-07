package com.example.shopping_back.auth;

import com.example.shopping_back.auth.dto.AuthUserView;
import com.example.shopping_back.auth.dto.LoginRequest;
import com.example.shopping_back.auth.dto.LoginResponse;
import com.example.shopping_back.auth.dto.ProfileUpdateRequest;
import com.example.shopping_back.auth.dto.RegisterRequest;
import com.example.shopping_back.auth.mapper.UserMapper;
import com.example.shopping_back.auth.model.StoredUser;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserMapper userMapper;
    private final Map<String, String> tokenToUsername = new ConcurrentHashMap<>();
    private final RestClient identityClient;

    public AuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
        this.identityClient = RestClient.create(System.getenv().getOrDefault("AUTH_BASE_URL", "http://127.0.0.1:8081"));
        ensureSchema();
        ensureSeedUser("demo", "demo123", "13800138000", "buyer");
        ensureSeedUser("seller", "seller123", "13700000000", "seller");
        ensureSeedUser("admin", "admin123", "13900000000", "admin");
    }

    public synchronized LoginResponse register(RegisterRequest req) {
        if (req == null || isBlank(req.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        if (isBlank(req.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        String username = req.getUsername().trim();
        if (userMapper.findByUsername(username) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        String phone = req.getPhone() == null ? "" : req.getPhone().trim();
        if (!phone.isEmpty() && userMapper.findByPhone(phone) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone already registered");
        }
        String role = normalizeRegisterRole(req.getRole());
        StoredUser user = new StoredUser(username, encoder.encode(req.getPassword()), phone, role);
        userMapper.insertUser(user);
        return issueToken(user);
    }

    public LoginResponse login(LoginRequest req) {
        StoredUser user = userMapper.findByUsername(req.getUsername().trim());
        if (user == null || !encoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if ("disabled".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account disabled");
        }
        return issueToken(user);
    }

    public AuthUserView me(String token) {
        StoredUser user = currentUser(token);
        return toView(user);
    }

    public AuthUserView updateProfile(String token, ProfileUpdateRequest req) {
        StoredUser user = currentUser(token);
        String avatarUrl = req == null ? "" : cleanAvatarUrl(req.getAvatarUrl());
        userMapper.updateAvatarUrl(user.getUserId(), avatarUrl);
        user.setAvatarUrl(avatarUrl);
        return toView(user);
    }

    private StoredUser currentUser(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        String username = tokenToUsername.get(token.trim());
        if (username == null) {
            username = resolveMicroserviceUsername(token.trim());
            tokenToUsername.put(token.trim(), username);
        }
        StoredUser user = userMapper.findByUsername(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        return user;
    }

    /**
     * 旧单体仍承载聊天、上传等兼容接口时，校验 user-service 签发的登录令牌。
     * 令牌只在远端校验成功后映射到同名兼容用户，不接受客户端自报身份。
     */
    private String resolveMicroserviceUsername(String token) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = identityClient.get().uri("/api/auth/me")
                    .header("Authorization", "Bearer " + token).retrieve().body(Map.class);
            Object dataValue = body == null ? null : body.get("data");
            if (dataValue instanceof Map<?, ?> data && data.get("username") instanceof String username
                    && !username.isBlank()) {
                return username.trim();
            }
        } catch (RuntimeException ignored) {
            // 对外统一返回 401，避免泄漏下游连接与鉴权细节。
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login expired");
    }

    public AuthUserView getUserById(Integer userId) {
        StoredUser user = userMapper.findById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return toView(user);
    }

    public List<AuthUserView> searchUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        List<StoredUser> users = userMapper.searchUsersByKeyword(escapeLike(keyword.trim()));
        List<AuthUserView> views = new java.util.ArrayList<>();
        for (StoredUser user : users) {
            views.add(toView(user));
        }
        return views;
    }

    /**
     * 转义 LIKE 通配符，避免用户输入 %、_、\ 被当作 SQL 通配符处理。
     */
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private LoginResponse issueToken(StoredUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenToUsername.put(token, user.getUsername());
        return new LoginResponse(token, toView(user));
    }

    private void ensureSchema() {
        if (userMapper.countUserColumn("role") == 0) {
            userMapper.addRoleColumn();
        }
        if (userMapper.countUserColumn("status") == 0) {
            userMapper.addStatusColumn();
        }
        if (userMapper.countUserColumn("avatar_url") == 0) {
            userMapper.addAvatarUrlColumn();
        }
    }

    private void ensureSeedUser(String username, String password, String phone, String role) {
        StoredUser existing = userMapper.findByUsername(username);
        if (existing == null) {
            userMapper.insertUser(new StoredUser(username, encoder.encode(password), phone, role));
            return;
        }
        if (!role.equals(normalizeRole(existing.getRole()))) {
            userMapper.updateRoleByUsername(username, role);
        }
    }

    private static AuthUserView toView(StoredUser user) {
        String phone = user.getPhone();
        String masked = "";
        if (phone != null && phone.length() == 11) {
            masked = phone.substring(0, 3) + "****" + phone.substring(7);
        }
        String role = normalizeRole(user.getRole());
        return new AuthUserView(
                user.getUserId(),
                user.getUsername(),
                masked,
                user.getCredit() != null ? user.getCredit() : 100,
                role,
                roleLabel(role),
                phone != null && phone.length() == 11,
                user.getStatus() == null ? "normal" : user.getStatus(),
                user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
    }

    private static String cleanAvatarUrl(String avatarUrl) {
        String value = avatarUrl == null ? "" : avatarUrl.trim();
        if (value.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar URL too long");
        }
        if (!value.isEmpty()
                && !value.startsWith("/files/")
                && !value.startsWith("http://")
                && !value.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid avatar URL");
        }
        return value;
    }

    private static String normalizeRegisterRole(String raw) {
        String role = normalizeRole(raw);
        if ("admin".equals(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin account cannot be registered");
        }
        return role;
    }

    private static String normalizeRole(String raw) {
        String role = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if ("seller".equals(role) || "卖家".equals(role)) {
            return "seller";
        }
        if ("admin".equals(role) || "管理员".equals(role)) {
            return "admin";
        }
        return "buyer";
    }

    private static String roleLabel(String role) {
        if ("seller".equals(role)) {
            return "卖家";
        }
        if ("admin".equals(role)) {
            return "管理员";
        }
        return "买家";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
