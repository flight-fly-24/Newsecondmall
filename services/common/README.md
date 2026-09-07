# common 公共模块（成员 5 维护）

微服务共用的对外契约与工具，各业务服务通过 Maven 依赖引入：

```xml
<dependency>
    <groupId>com.example.mall</groupId>
    <artifactId>common</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 1. 响应格式：`com.example.mall.common.dto.ApiResult`

所有 Controller 返回值统一用 `ApiResult` 包装，与旧单体对外表现完全一致：

```java
@GetMapping public ApiResult<List<ProductView>> list() {
    return ApiResult.ok(service.list());
}
```

序列化结果（`code=0` 成功、`message="ok"`、数据在 `data`）：

```json
{"code":0,"message":"ok","data":[...]}
```

错误统一抛 `ResponseStatusException`，由各服务全局异常处理转成 `ApiResult` + 对应 HTTP 状态码。

## 2. 鉴权：`com.example.mall.common.AuthClient`（过渡方案）

**先看这里的坑：** 旧单体 token 是「随机串 → 用户名」的内存映射，token 不含用户信息，微服务无法本地解析。

过渡期微服务把 token 转发回旧单体校验：

```java
// 注入 baseUrl 指向旧单体 backend:8080 的 RestClient
AuthClient auth = new AuthClient(restClient);
long userId = auth.requireUser(authorization);   // 未登录抛 401，服务不可用抛 503
```

**最终方案建议换 JWT**（自包含 token）：各服务用共享密钥本地解析、不再回源，需改旧单体登录逻辑 + 各服务加 JWT 校验，由成员 1 牵头、全员配合。

## 3. 各服务接入清单

| 服务 | 要做的事 |
|---|---|
| user-service | 依赖 common；登录/注册返回 `ApiResult`；补内部鉴权校验接口 |
| catalog-service | 依赖 common；`/api/products` 返回 `ApiResult`；鉴权接口用 `AuthClient` |
| trade-service | 依赖 common；`/api/orders` 返回 `ApiResult`；下单用 `AuthClient` 取代明文 `buyerId` |
| interaction-service | 依赖 common；`/api/topics` 返回 `ApiResult`；鉴权接口用 `AuthClient` |
