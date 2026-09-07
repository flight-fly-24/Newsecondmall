# API 自动化测试

可直接接入 CI 的 Postman Collection v2.1 文件为：

`soft-shop-api.postman_collection.json`

Newman 报告生成在 `reports/`，不提交到 Git。

运行旧单体 API：

```powershell
npm ci
npm run test:api
```

运行器会重置测试夹具、执行 Newman，并在失败时返回非零退出码，同时生成 JUnit XML、HTML 和 JSON 报告。

The collection includes UC-linked coverage for UC03 and UC05 through UC15.
After replacing or regenerating the base Postman collection, rebuild those cases with:

```powershell
node tests/api/extend-uc-coverage.mjs
```

Use another deployed endpoint with `API_BASE_URL`, or pass
`--base-url=https://example.test` after `--` in the npm command.

## 四微服务完整回归

针对已启动环境运行：

```powershell
npm run test:api:microservices
```

自动创建并销毁独立 Compose 环境：

```powershell
npm run test:api:microservices:compose
```

当前集合包含 82 个请求、163 个断言，覆盖 user、catalog、trade、interaction、网关路由、
商品审核、跨服务下单和关联内容。2026-09-01 最近一次完整执行为 82/82 请求通过、
163/163 断言通过。报告位于 `reports/microservices/`，失败摘要为 `failure-summary.md`。
