> ### 📌 个人贡献说明
> 本项目为团队项目，由多人协作完成。  
> **本人独立负责并完整实现了“商品搜索与推荐”子系统**，具体包括：
> - 关键词搜索（分词、模糊匹配、自动补全、精确搜索、无结果兜底）
> - 多级分类筛选与多维度排序（销量/信用/智能综合排序）
> - 热门推荐与个性化推荐
> - 历史搜索记录管理与以图搜图功能
> 
> 其余模块（用户中心、购物车、订单、消息等）由团队成员共同协作完成。
> 
> ---
> 
# 松果集市

松果集市是一个面向新品与二手商品流转的交易平台，支持商品浏览、发布与审核、购物车与订单、用户中心、即时消息、社区互动和 AI 辅助议价。

## 技术栈

- 前端：uni-app、Vue 3，支持 H5 和微信小程序。
- 后端：Spring Boot 3、MyBatis、MySQL、WebSocket/STOMP。
- 开发平台：CodeArts Repo、看板/Scrum、流水线与制品管理。

## 环境版本

- Java 17（Eclipse Temurin 17，见 `deploy/backend.Dockerfile` 与 CI 镜像 `maven3.9.5-jdk17`）。
- Maven 3.9.5（CI 流水线镜像）；Docker 构建阶段使用 `maven:3-eclipse-temurin-17`。
- Node.js 18（见 `deploy/frontend.Dockerfile` 的 `node:18-alpine` 与 CI 镜像 `nodejs18`）。
- MySQL 8.4（见 `deploy/docker-compose.yml` 的 `mysql:8.4`；脚本兼容 MySQL 8.0+，InnoDB / utf8mb4）。
- Nginx 1.27（前端静态资源容器，见 `deploy/frontend.Dockerfile`）。

## 目录

- `shopping_front/`：前端工程。
- `shopping_back/shopping_back/`：后端工程。
- `services/`：按中期设计拆分的用户、商品、交易、互动与智能 4 个独立业务服务。
- `shopping_back/shopping_back/doc/db.sql`：数据库初始化脚本。
- `docs/`：需求、接口、测试、部署和用户文档。

## 本地启动

1. 按 [环境搭建说明](ENV_SETUP.md) 创建数据库并配置本地凭据。
2. 在 `shopping_back/shopping_back/` 启动 Spring Boot，确认 `http://127.0.0.1:8080/api/products` 返回数据。
3. 首次运行前端时，在 `shopping_front/` 执行 `npm install`。
4. 使用 HBuilderX 打开 `shopping_front/`，运行到浏览器；默认地址为 `http://localhost:5173`。

任何密码、Token 和 AI Key 都不得提交到仓库。请通过环境变量或未纳入版本控制的 `application-local.properties` 配置。

## 测试账号

初始数据内置以下账号（密码哈希为 BCrypt，完整说明见 [deploy/db/README.md](deploy/db/README.md) 第五节）：

| 账号 | 角色 | 密码 |
|---|---|---|
| demo | 买家 | demo123 |
| seller | 卖家 | seller123 |
| admin | 管理员 | admin123 |
| life_seller | 卖家 | seller123 |
| book_seller | 卖家 | seller123 |

前端登录页（`shopping_front/pages/auth/login.vue`）也提供演示账号快捷入口：买家 `demo / demo123`、管理员 `admin / admin123`。

## 初始数据

- Docker / 本地部署：MySQL 容器首次启动时自动执行 `deploy/db/init/` 下的 `000-create-microservice-databases.sql`、`001-schema.sql`、`002-seed.sql`（非空库自动跳过），与版本化迁移 `deploy/db/migrations/V*.sql` 内容一致。
- K8s 部署：等价脚本打包在 `k8s/mysql-init-configmap.yaml`，由 `k8s/mysql.yaml` 挂载到 `/docker-entrypoint-initdb.d`；schema 变更后用 `deploy/db/tools/build-k8s-configmap.sh` 重新生成。

## 健康检查

- 单体后端：`http://127.0.0.1:8080/api/products` 返回数据即正常（Docker 镜像 HEALTHCHECK 同地址）。
- 前端容器：`http://127.0.0.1/`（见 `deploy/frontend.Dockerfile` HEALTHCHECK）。
- K8s 就绪探针（namespace `shop`，见 `k8s/*.yaml`）：
  - `backend`、`catalog-service`：`GET /api/products`
  - `interaction-service`：`GET /api/topics`（启动探针 `/api/interaction/health`）
  - `user-service`：`GET /actuator/health`
  - `trade-service`：TCP 端口 8083
  - `frontend`：`GET /`
- 一键巡检脚本 `ops/remote-health.sh`（依赖 kubectl 和 curl）：

```bash
ops/remote-health.sh [健康检查入口URL，默认 http://127.0.0.1] [期望镜像TAG] [日志目录]
```

脚本会核对各 Deployment 副本与镜像 TAG，并检查首页和 `/api/products` 均返回 HTTP 200。

## Docker 一键部署

新机器只需安装 Git 和 Docker Desktop，不需要预先安装 Maven、Java、Node.js 或 HBuilderX。

```powershell
Copy-Item deploy\.env.example deploy\.env
# 编辑 deploy\.env，替换数据库密码和 PUBLIC_ORIGIN
docker compose --env-file deploy\.env -f deploy\docker-compose.yml up -d --build
docker compose --env-file deploy\.env -f deploy\docker-compose.yml ps
```

也可双击 `NewSecondMall-启动面板.cmd`，选择第 9 项。Docker 会在多阶段构建中自动安装依赖、编译前后端并启动 MySQL、backend 和 frontend 三个容器。

## 文档入口

- [项目详细说明](docs/README.md)
- [接口文档](docs/api.md)
- [测试文档](docs/测试文档.md)
- [部署文档](docs/部署文档.md)
- [用户手册](docs/用户手册.md)
- [代码与文档同步规范](docs/change-policy.md)
- [微服务拆分设计、接口与表归属](docs/microservices-split.md)

## 当前改进方向

项目将先建立测试、部署、安全、性能和可观测性基线，再从模块化单体逐步演进。只有在边界、数据所有权和回归测试明确后，才抽取独立服务，避免为了“微服务”而增加不必要的分布式复杂度。
