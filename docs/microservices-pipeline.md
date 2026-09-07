# 微服务流水线改造

本仓库使用一条统一发布流水线。所有可部署组件使用同一不可变版本
`release-<PIPELINE_NUMBER>-<COMMIT_ID_SHORTER>`，通过同一份 Kustomize
清单部署和回滚；不要为每个服务创建独立的生产部署流水线。

## CodeArts 任务编排

在 `NewSecondMall-CI-CD` 的可视化编辑页中按以下方式调整：

1. 保留现有阶段顺序：编译构建、单元测试、集成测试、端到端测试、镜像制作、部署到 Kubernetes、健康检查。
2. 单元测试阶段有两种互斥方式：资源优先时保留当前 `NewSecondMall-unit-test`（它同时校验单体和全部微服务模块）；速度优先时，用 `.cloudbuild/monolith-unit-test.yml` 的单体任务和 `.cloudbuild/microservice-unit-test.yml` 的四个服务任务替换它。
3. 速度优先的配置中，将单体任务和四个微服务任务放在“单元测试”阶段的**并行任务**中；微服务任务分别传入 `SERVICE_MODULE=user-service`、`catalog-service`、`trade-service`、`interaction-service`。所有并行任务完成后才能进入集成测试。
4. 集成测试、端到端测试、镜像制作、Kubernetes 部署、健康检查保持**串行**。这些阶段依赖完整服务集合和同一版本号，不能按服务各自发布。
5. 镜像制作继续使用 `NewSecondMall-publish-images` 和 `.cloudbuild/publish-images.yml`。该任务会发布 backend、frontend 及四个微服务的同一 release tag。

## 本次接入的服务

`ops/services.conf` 是发布服务清单；新增服务时必须在此清单、`k8s/kustomization.yaml` 和对应 Kubernetes Deployment 中使用一致的镜像名。发布门禁、元数据、部署诊断和健康检查会从该清单或完整服务列表校验：

- `user-service` / `shop-user-service`
- `catalog-service` / `shop-catalog-service`
- `trade-service` / `trade-service`
- `interaction-service` / `interaction-service`

现有集成/API 阶段仍用于单体兼容链路；完整微服务调用由 E2E 的 Docker Compose 环境覆盖。后续如需把集成/API 也迁移至微服务，应新建一个 Compose 驱动的测试任务后替换该阶段，不应与生产发布阶段并行执行。
