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

## 目录

- `shopping_front/`：前端工程。
- `shopping_back/shopping_back/`：后端工程。
- `shopping_back/shopping_back/doc/db.sql`：数据库初始化脚本。
- `docs/`：需求、接口、测试、部署和用户文档。

## 本地启动

1. 按 [环境搭建说明](ENV_SETUP.md) 创建数据库并配置本地凭据。
2. 在 `shopping_back/shopping_back/` 启动 Spring Boot，确认 `http://127.0.0.1:8080/api/products` 返回数据。
3. 首次运行前端时，在 `shopping_front/` 执行 `npm install`。
4. 使用 HBuilderX 打开 `shopping_front/`，运行到浏览器；默认地址为 `http://localhost:5173`。

任何密码、Token 和 AI Key 都不得提交到仓库。请通过环境变量或未纳入版本控制的 `application-local.properties` 配置。

## 文档入口

- [项目详细说明](docs/README.md)
- [接口文档](docs/api.md)
- [测试文档](docs/测试文档.md)
- [部署文档](docs/部署文档.md)
- [用户手册](docs/用户手册.md)
- [代码与文档同步规范](docs/change-policy.md)

## 当前改进方向

项目将先建立测试、部署、安全、性能和可观测性基线，再从模块化单体逐步演进。只有在边界、数据所有权和回归测试明确后，才抽取独立服务，避免为了“微服务”而增加不必要的分布式复杂度。
