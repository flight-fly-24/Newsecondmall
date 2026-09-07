package com.example.shopping_back.auth;

import com.example.shopping_back.common.dto.ApiResult;
import com.example.shopping_back.auth.dto.AuthUserView;
import com.example.shopping_back.auth.dto.LoginRequest;
import com.example.shopping_back.auth.dto.LoginResponse;
import com.example.shopping_back.auth.dto.ProfileUpdateRequest;
import com.example.shopping_back.auth.dto.RegisterRequest;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResult<LoginResponse> register(@Valid @RequestBody RegisterRequest body) {
        return ApiResult.ok(authService.register(body));
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        return ApiResult.ok(authService.login(body));
    }

    @GetMapping("/me")
    public ApiResult<AuthUserView> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = bearerToken(authorization);
        return ApiResult.ok(authService.me(token));
    }

    @PutMapping("/me/profile")
    public ApiResult<AuthUserView> updateProfile(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @RequestBody ProfileUpdateRequest body) {
        String token = bearerToken(authorization);
        return ApiResult.ok(authService.updateProfile(token, body));
    }

    @GetMapping("/search-users")
    public ApiResult<List<AuthUserView>> searchUsers(@RequestParam("keyword") String keyword) {
        return ApiResult.ok(authService.searchUsers(keyword));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String v = authorization.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return v.substring(7).trim();
        }
        // 不携带合法的 Bearer 前缀一律视为未携带 Token
        return null;
    }
}
