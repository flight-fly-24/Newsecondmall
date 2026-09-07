# 四业务微服务迁移基线

本目录严格采用《第三组微服务划分.pdf》的四个业务边界。每个服务都有独立 `pom.xml`、启动类、数据源、`schema.sql`、测试和 Dockerfile，可单独构建、测试、部署。

| 服务 | 端口 | 数据库 | 已落地的迁移切片 |
|---|---:|---|---|
| `user-service` | 8081 | `user_db` | 用户查询、内部用户/地址校验 |
| `catalog-service` | 8082 | `catalog_db` | 商品发布/查询、内部商品快照、幂等标记已售 |
| `trade-service` | 8083 | `trade_db` | 校验用户和地址、查询商品快照、幂等下单、标记已售与补偿状态 |
| `interaction-service` | 8084 | `interaction_db` | 话题创建与查询；为帖子、评论、聊天和 AI 保留独立边界 |

旧单体暂时保留以兼容尚未迁移的 API。新功能不得通过跨库 SQL 访问其他服务；迁移以 `docs/microservices-split.md` 的接口归属为准。

## 自动化测试

从仓库根目录运行：

```powershell
.\services\test-services.ps1
```

测试使用 H2 内存数据库，不需要本地 MySQL。单独测试某个服务：

```powershell
.\shopping_back\shopping_back\mvnw.cmd -f .\services\trade-service\pom.xml test
```

## 独立打包与镜像

```powershell
.\shopping_back\shopping_back\mvnw.cmd -f .\services\catalog-service\pom.xml clean package
docker build -t newsecondmall/catalog-service:1.0.0 .\services\catalog-service
```

其他服务替换目录名即可。运行时分别注入 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`；交易服务还需设置 `USER_BASE_URL` 和 `CATALOG_BASE_URL`。生产密码不得提交到仓库。
