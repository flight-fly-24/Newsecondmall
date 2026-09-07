package com.example.mall.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
public class ProductAuditController {
    private final ProductService service;
    private final HttpAuthClient authClient;

    public ProductAuditController(ProductService service, HttpAuthClient authClient) {
        this.service = service;
        this.authClient = authClient;
    }

    @GetMapping
    public ApiResult<List<ProductService.StorefrontProduct>> pending(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        return ApiResult.ok(service.pending().stream().map(service::toStorefront).toList());
    }

    @PostMapping("/{id}")
    public ApiResult<ProductService.AuditResult> audit(@PathVariable long id,
            @Valid @RequestBody AuditRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        return ApiResult.ok(service.audit(id, request.action(), request.reason()));
    }

    private void requireAdmin(String authorization) {
        if (!authClient.requireUser(authorization).isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户无管理员权限");
        }
    }

    public record AuditRequest(@NotBlank String action, String reason) {}
}
