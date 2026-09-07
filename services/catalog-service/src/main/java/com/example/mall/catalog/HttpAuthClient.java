package com.example.mall.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpAuthClient {
    private final RestClient client;

    public HttpAuthClient(@Value("${app.auth.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(500);
        requestFactory.setReadTimeout(2000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public CatalogUser requireUser(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        try {
            JsonNode body = client.get().uri("/api/auth/me")
                    .header("Authorization", authorization)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = body == null ? null : body.path("data");
            if (data == null || data.isMissingNode() || data.isNull() || !data.hasNonNull("userId")) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
            }
            String status = text(data, "status");
            if (!status.isBlank() && !"normal".equalsIgnoreCase(status)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号不可用");
            }
            int credit = data.hasNonNull("credit") ? data.get("credit").asInt() : 100;
            return new CatalogUser(
                    data.get("userId").asLong(),
                    text(data, "role"),
                    text(data, "username"),
                    credit);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.valueOf(status), "请先登录");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "登录服务暂时不可用", exception);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "登录服务暂时不可用", exception);
        }
    }

    private static String text(JsonNode data, String field) {
        JsonNode node = data.path(field);
        return node.isMissingNode() || node.isNull() ? "" : node.asText();
    }
}
