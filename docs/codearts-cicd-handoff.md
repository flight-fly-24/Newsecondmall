# CodeArts CI/CD 交接说明

## 目标流水线

为当前功能分支配置手工触发（先不要给 `master` Push 开自动部署）。阶段严格串行：

```text
代码检出 → 编译 → 单元测试 → API 测试 → E2E 测试
        → 镜像制作 → 部署到 Kubernetes → 健康检查
```

任一步失败时后续阶段不得运行。

| 阶段 | 仓库文件 |
| --- | --- |
| E2E | `.cloudbuild/e2e.yml` |
| 镜像制作 | `.cloudbuild/publish-images.yml` |
| 部署 | `.cloudbuild/deploy-k8s.yml` |
| 健康检查 | `.cloudbuild/health.yml` |

`.cloudbuild/release.yml` 已停用（会拉 `cloudbuild@docker20.10` 超时）。不要把镜像和部署合成一张卡。

## 流水线参数与凭据

CodeArts 系统参数直接传入构建任务：`PIPELINE_NUMBER`、`COMMIT_ID`、`COMMIT_ID_SHORTER`（提交号前 8 位）。最终版本固定生成为：

```text
release-${PIPELINE_NUMBER}-${COMMIT_ID_SHORTER}
```

`docker` 插件不能跑 `$(cut)` / `$(cat)`，但可以替换系统参数 `${COMMIT_ID_SHORTER}`，因此 bash 与 docker `-t` 都用这 8 位。不要自定义同名参数。

在 CodeArts 构建任务参数中配置，禁止填写在仓库文件中：

| 名称 | 用途 |
| --- | --- |
| `SWR_USERNAME` | 登录华北-北京四 SWR；给镜像制作任务。不要勾选私密参数，否则 docker 插件读不到 |
| `SWR_PASSWORD` | 同上 |
| `ECS_HOST` | ECS 地址，当前为 `120.46.222.10` |
| `ECS_USER` | 部署用户，当前为 `root` |
| `ECS_SSH_PRIVATE_KEY` | CodeArts 专用部署私钥；平台注入文件时改传 `ECS_SSH_KEY_FILE` |
| `ECS_HOST_KEY` | 已人工核对的 SSH known_hosts 完整行 |
| `CI_DB_PASSWORD` | E2E 临时数据库用户密码 |
| `CI_MYSQL_ROOT_PASSWORD` | E2E 临时 MySQL root 密码 |

可选参数：`HEALTHCHECK_BASE_URL` 默认 `http://127.0.0.1`；`ROLLOUT_TIMEOUT` 默认 `300s`；`FAILURE_DEMO` 默认 `false`，仅限人工回滚演示，禁止加入 master 自动触发参数。

## 阶段配置

1. 编译和单元测试：后端执行 Maven test 并保留 Surefire、JaCoCo；前端安装依赖并构建。
2. API 测试：运行 `npm run test:api`，发布 `tests/api/reports/`。
3. E2E 测试：运行 `.cloudbuild/e2e.yml`，发布 Surefire、截图、页面源码和 Compose 日志。
4. 镜像制作：docker 插件 login/build/push 三个 SWR 镜像（backend、frontend、trade-service），Tag 相同且不可变。
5. 部署：SSH 到 ECS 执行 `ops/remote-deploy.sh`。失败先上传 `ci-artifacts/**`，再回滚上一成功版本，流水线仍为失败。
6. 健康检查：再确认 Pod `1/1`、Deployment 注解、`/` 与 `/api/products` HTTP 200。
7. 部署与健康检查必须串行；新的执行必须排队，不能同时操作 ECS。

## 交付证据

- `ci-artifacts/release/release-metadata.json`：Commit、流水线编号、镜像 Tag。
- `ci-artifacts/kubernetes-deploy.log`：SSH 部署与健康检查日志。
- `ci-artifacts/deploy/`：describe、events 和 Pod 日志。
- `ci-artifacts/health/health.log`：独立健康检查日志。
- CodeArts 流水线执行历史：至少保留一次完整成功和一次受控失败记录。

## 验收标准

- 三个 SWR 仓库（`shop-backend`、`shop-frontend`、`trade-service`）出现相同的新 Tag，旧 Tag 未被覆盖。
- MySQL、后端、前端、trade-service 均为 `1/1 Running`。
- Deployment 注解中的版本、Commit、流水线编号与发布元数据一致。
- 首页和 `/api/products` 返回 HTTP 200。
- 测试失败时不制作镜像；部署失败时恢复上一成功版本，但流水线仍显示失败。

## 受控失败演示

得到 ECS 负责人确认后，手工执行一次 `FAILURE_DEMO=true` 的**部署**任务。脚本让本次前端引用不存在的 Tag，旧 Pod 在滚动更新期间保持服务；超时后采集 `ImagePullBackOff` 现场并回滚。演示结束后确认首页和 API 为 200，并保留该次失败记录。
