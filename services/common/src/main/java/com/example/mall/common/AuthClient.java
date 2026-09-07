package com.example.mall.common;

import com.example.mall.common.dto.ApiResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 鉴权过渡方案（token 为旧单体内存态时的临时方案）。
 *
 * 背景：旧单体发的 token 是「随机串 → 用户名」的内存映射，token 本身不含用户信息，
 * 微服务进程无法本地解析。因此过渡期只能把 token 转发回旧单体校验。
 *
 * 用法：微服务注入一个 baseUrl 指向旧单体（backend:8080）的 RestClient 后：
 *
 *   AuthClient authClient = new AuthClient(restClient);
 *   long userId = authClient.requireUser(authorization);   // 返回当前登录用户 id
 */
public class AuthClient {

    private final RestClient client;

    public AuthClient(RestClient client) {
        this.client = client;
    }

    /**
     * 解析 token，未登录或失效返回 null（供浏览类接口使用）。
     */
    public AuthUser optionalUser(String authorization) {
        String token = bearerToken(authorization);
        if (token == null) {
            return null;
        }
        try {
            return fetchUser(token);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 401) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 校验 token 并返回当前用户。未登录 / token 失效抛 401；鉴权服务不可用抛 503。
     */
    public AuthUser requireProfile(String authorization) {
        String token = bearerToken(authorization);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        return fetchUser(token);
    }

    /**
     * 校验 token 并返回当前用户 id。
     * 未登录 / token 失效抛 401；鉴权服务不可用抛 503。
     */
    public long requireUser(String authorization) {
        return requireProfile(authorization).userId();
    }

    private AuthUser fetchUser(String token) {
        try {
            ApiResult<AuthUser> result = client.get()
                    .uri("/api/auth/me")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResult<AuthUser>>() {});
            if (result == null || result.getCode() != 0 || result.getData() == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login expired");
            }
            return result.getData();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login expired");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "auth service unavailable", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "auth service unavailable", e);
        }
    }

    private static String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String v = authorization.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return v.substring(7).trim();
        }
        return null;
    }

    /** 只取鉴权需要的字段，旧单体 /api/auth/me 返回的是 ApiResult&lt;AuthUserView&gt;。 */
    public record AuthUser(long userId, String username, String role, String status) {}
}
