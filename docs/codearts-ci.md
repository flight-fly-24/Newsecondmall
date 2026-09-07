# CodeArts CI

作业要的是**流水线多个板块**，不是把所有步骤塞进一个构建任务。

华为云里真正干活的还是「编译构建」任务（自带 checkout）。流水线只负责串卡片：上一张失败，后面不跑。

当前规划：

| 流水线板块 | 实现方式 | 仓库文件 |
| --- | --- | --- |
| 1. 构建 | 已有构建任务 NewSecondMall | `.cloudbuild/build.yml` |
| 2. 单元测试 | 再建一个构建任务，流水线里第二张卡片 | `.cloudbuild/unit-test.yml` |
| 3. 启动测试环境 + 集成测试 + 接口测试 | 再建一个构建任务，流水线第三张卡片 | `.cloudbuild/integration-api.yml` |
| 4. 端到端测试 | 再建构建任务，流水线第四张卡片 | `.cloudbuild/e2e.yml` |
| 5. 镜像制作 | 再建构建任务，流水线第五张卡片 | `.cloudbuild/publish-images.yml` |
| 6. 部署到 Kubernetes | 再建构建任务，流水线第六张卡片；必须串行 | `.cloudbuild/deploy-k8s.yml` |
| 7. 健康检查 | 再建构建任务，流水线第七张卡片 | `.cloudbuild/health.yml` |

不要用流水线里的「下载仓库」+ shell 自己编译。那条路会空目录、`auth info is empty`。

## 为什么构建步骤里看不到「单元测试」

构建任务左侧列表显示的是 YAML 里每个插件的 `name`：

- `checkout`
- `build-frontend`
- `build-backend`

`wait_job_depends`、`环境准备` 是平台自己加的，不是仓库写的。

编译任务里的 `build-backend` 只打包，不跑测试。单元测试在独立任务 `NewSecondMall-unit-test` 里。

## 编译构建（第 1 张卡片）

文件：`.cloudbuild/build.yml`

1. 检出（`${codeBranch}`）
2. Node 18：`npm ci` + `npm run build:h5`
3. Maven JDK 17：`mvn -DskipTests package`（只编译打包；单元测试在另一张卡片）

参数在**该构建任务**的「参数设置」里：

- `CI_DB_PASSWORD` = `CiShop2026_Test`
- `CI_MYSQL_ROOT_PASSWORD` = `CiRoot2026_Test`

规格必须是 `2U8G`。Maven 必须 `cache: false`，命令用 `mvn` 不用 `./mvnw`。

## 单元测试（第 2 张卡片）

文件：`.cloudbuild/unit-test.yml`

这是另一个构建任务，不要改现有 NewSecondMall 那份 YAML 路径。步骤：checkout → `mvn test jacoco:report`（含队友补充的地址/购物车/中心/聊天/店铺单测），并上传 Surefire 与 JaCoCo 报告。

控制台操作：

1. 编译构建 → 新建任务，名称例如 `NewSecondMall-unit-test`。
2. 源码仍选仓库 NewSecondMall，默认分支 `feature/lqy-first-stage`。
3. 构建方式选 YAML，路径填 `.cloudbuild/unit-test.yml`。
4. 参数同样填 `CI_DB_PASSWORD`、`CI_MYSQL_ROOT_PASSWORD`，规格 `2U8G`。
5. 先单独执行一次，确认步骤列表出现 `unit-test`。
6. 打开流水线 → 任务编排 → 在「构建」后面新增阶段，名称「单元测试」。
7. 拖入 **Build 构建**，任务选 `NewSecondMall-unit-test`，依赖上一张「构建」。
8. 保存并执行，分支 `feature/lqy-first-stage`。

流水线画布上应看到两块：构建 → 单元测试。点进第二块，步骤里才会出现名为 `unit-test` 的行。

`NewSecondMall` 任务不要加 `CB_BUILD_YAML_PATH`。每个构建任务都要有 `codeBranch`，默认值 `feature/lqy-first-stage`。

## 集成与接口测试（第 3 张卡片）

文件：`.cloudbuild/integration-api.yml`  
脚本：`scripts/ci-integration-api.sh`

一张卡片里顺序做三件事：华为云镜像下载 MySQL 二进制包并启动后端 → `tests/blackbox/smoke.sh`（接口冒烟）→ Newman `npm run test:api`（含 UC03、UC05–UC15 扩展用例）。必须放在同一个步骤里，环境才能一直活着。Newman 报告上传为 `api-newman-reports.tgz`。

不要用 `cloudbuild@docker20.10`、Docker Compose，也不要在构建镜像里 `yum install`：执行机访问不了 Docker Hub，且 yum 源是空的。

控制台操作（和单元测试任务同一套办法）：

1. 编译构建 → 新建任务，名称 `NewSecondMall-integration-api`。
2. 源码仍选 NewSecondMall，默认分支 `feature/lqy-first-stage`。
3. 先随便保存，**不要**在代码化里改 `build.yml`。
4. 参数设置增加：

| 名称 | 默认值 |
| --- | --- |
| `CB_BUILD_YAML_PATH` | `.cloudbuild/integration-api.yml` |
| `codeBranch` | `feature/lqy-first-stage`（运行时设置打开） |
| `CI_DB_PASSWORD` | `CiShop2026_Test` |
| `CI_MYSQL_ROOT_PASSWORD` | `CiRoot2026_Test` |

5. 规格 `2U8G`。保存后从任务列表点 **执行**，不要从代码化点保存并执行。
6. 单独跑绿后，流水线在「单元测试」后新增阶段「集成测试」，拖入 Build，任务选 `NewSecondMall-integration-api`，依赖单元测试。

执行记录里步骤名是 `start-env-integration-api`。日志里会有 `1/3 启动测试环境`、`2/3 集成测试`、`3/3 接口测试`。

## 端到端测试（第 4 张卡片）

文件：`.cloudbuild/e2e.yml`  
脚本：`scripts/ci-e2e-incontainer.sh`（容器内执行）、`scripts/ci-e2e-extract.sh`（产物提取）。`scripts/ci-e2e.sh` 是 Compose 版，只用于有真实 Docker 守护进程的环境，CodeArts 不要引用。  
来源：队友分支 `test/e2e-selenium`（容器化 Selenium + Chrome）。

这张卡片在 `docker build` 容器内启动完整微服务拓扑：MariaDB（shop_db + user/catalog/trade/interaction 四个库）、旧单体 8080（未迁移接口兜底）、四个微服务 8081-8084、Nginx 网关 `18080`（路由与 `deploy/nginx/default.conf` 一致）、Chromium。先跑微服务 Newman 全量回归，再跑 Selenium UI。必须接在「集成测试」后面：接口都过不了就不必开浏览器。

不要用 `cloudbuild@docker20.10`（会去 Docker Hub 超时）。不要用 `docker run` / `docker compose`：`docker` 插件只允许 `build/tag/push/pull/login/logout/save`。`sh` 没有 Docker socket；`swr` dockerindocker 在 CodeArts 里没有特权，起不了 dockerd（iptables Permission denied）。

因此 E2E 改成 **`docker pull` + `docker build` + `docker save`**：在构建镜像时安装 Chromium、MariaDB、Nginx，跑 `scripts/ci-e2e-incontainer.sh`，再用一个很小的 export 镜像把 Surefire 和截图带出来。

CodeArts 勾选 **私密参数** 后，`docker` 插件读不到 `CI_DB_PASSWORD`（`#20260827.8` 立刻报密码缺失，产物只有 0.12KB）。这两个是测试库密码，文档里本来就是明文，**不要勾选私密参数**。YAML 会把 `${CI_DB_PASSWORD}` 写进 `--build-arg`；另外 `e2e-secrets` 还会写一份 `tests/e2e/.ci-secrets` 作兜底（已 gitignore，不要提交）。

控制台操作：

1. 编译构建 → 新建任务，名称 `NewSecondMall-e2e`。
2. 源码选 NewSecondMall，默认分支 `feature/lqy-first-stage`。
3. 不要改代码化里的 `build.yml`，点取消或先保存空任务。
4. 参数设置：

| 名称 | 说明 |
| --- | --- |
| `CB_BUILD_YAML_PATH` | `.cloudbuild/e2e.yml` |
| `codeBranch` | `feature/lqy-first-stage`（打开运行时设置） |
| `CI_DB_PASSWORD` | `CiShop2026_Test`，**不要**勾选私密参数 |
| `CI_MYSQL_ROOT_PASSWORD` | `CiRoot2026_Test`，**不要**勾选私密参数 |

5. 规格 `2U8G`。保存后从任务列表点 **执行**。
6. 单独能跑起来后，流水线在「集成测试」后新增阶段「端到端测试」，拖入 Build，任务选 `NewSecondMall-e2e`，依赖集成测试。

产物（失败也会尽量上传）：

- `e2e-surefire-reports.tgz`：Surefire / JUnit XML（`e2e-tests/target/surefire-reports/`）
- `e2e-artifacts.tgz`：截图、页面源码、各服务日志（`e2e-tests/target/e2e-artifacts/`）
- `e2e-api-reports.tgz`：微服务 Newman JUnit/HTML/JSON 和失败摘要（`tests/api/reports/microservices/`）
- `e2e-run-results.tgz`：阶段摘要（`tests/e2e/results/`）

构建任务会把这些包传到软件发布库目录 `/NewSecondMall-e2e/`。流水线里打开该 Build 插件，勾选把构建产物作为流水线产物；下载处应能看到这些 `.tgz`。

步骤顺序：`e2e-secrets`（写入 `.ci-secrets`）→ `e2e-test`（docker pull/build/save，允许失败以便导出产物）→ `e2e-extract` → 上传四个 `.tgz` → `e2e-gate`。

成功时日志应出现 `===== 启动 MariaDB =====` 和 `===== 启动微服务 =====`，而不是立刻 `CI_DB_PASSWORD is required`。

`e2e-test` 用 CodeArts 允许的 Docker 命令在镜像构建里跑浏览器用例。基础镜像走同区域 SWR：`swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/debian:bookworm-slim`（不要 DaoCloud / Docker Hub，`#20260827.12` 在 DaoCloud 第一层直接卡死）。镜像里再用华为云 Debian 源安装 JDK、Maven、Chromium。

不要在 `e2e-test` 步骤里跑 `bash scripts/ci-e2e.sh` 或任何 `docker run` / `docker compose`：docker 插件只允许 `build/tag/push/pull/save/login`，会立刻失败（`#66` 端到端阶段 12 秒失败、产物为空就是这个原因）。完整拓扑已收进 `tests/e2e/Dockerfile.ci` 的镜像构建里。

若 `e2e-test` 报找不到 socket 或拉镜像失败，把该步骤完整日志发我。

## 镜像制作（第 5 张卡片）

文件：`.cloudbuild/publish-images.yml`  
脚本：`scripts/ci-prepare-release.sh`、`scripts/ci-finalize-publish.sh`

Tag 固定为 `release-${PIPELINE_NUMBER}-${COMMIT_ID_SHORTER}`（系统参数：提交号前 8 位），backend / frontend / trade-service 推同一个新 Tag。同一 Tag 已存在时拒绝覆盖，要发新版本必须先有新提交或新的流水线号。不要用 `cloudbuild@docker20.10`、不要 `docker buildx`。`docker` 插件只做 `login/pull/build/push`，不能跑 `$(cat)` / `cut`，基础镜像走 SWR `ddn-k8s`。

CodeArts 勾选 **私密参数** 后，`docker` 插件读不到 `SWR_USERNAME` / `SWR_PASSWORD`（和 E2E 密码同一限制）。这两个参数 **不要勾选私密**，只放在构建任务里，禁止写入仓库。ECS 私钥给后面两张卡，那些用 shell，可以勾选私密。

控制台操作：

1. 编译构建 → 新建任务，名称 `NewSecondMall-publish-images`。
2. 源码选 NewSecondMall，默认分支 `feature/lqy-first-stage`。
3. 不要改代码化里的 `build.yml`。
4. 参数设置：

| 名称 | 说明 |
| --- | --- |
| `CB_BUILD_YAML_PATH` | `.cloudbuild/publish-images.yml` |
| `codeBranch` | `feature/lqy-first-stage`（打开运行时设置） |
| `PIPELINE_NUMBER` | 流水线执行序号，打开运行时设置；正式流水线里映射系统参数「流水线运行序号」 |
| `SWR_USERNAME` | SWR 登录用户，**不要**勾选私密 |
| `SWR_PASSWORD` | SWR 登录密码，**不要**勾选私密 |

不要再建 `COMMIT_ID`、`COMMIT_ID_SHORTER`（系统已有）。不要再填自定义 `COMMIT_ID_SHORT=manual00`。Tag 为 `release-<流水线号>-<提交号前8位>`，例如 `release-1-809eed02`。

5. 规格 `2U8G`。保存后从任务列表点 **执行**。
6. 流水线在「端到端测试」后新增阶段「镜像制作」，拖入 Build，任务选 `NewSecondMall-publish-images`，依赖 E2E。勾选把构建产物作为流水线产物。

产物：`ci-artifacts/release/`（kustomization、release-metadata.json、tgz）。

## 部署到 Kubernetes（第 6 张卡片）

文件：`.cloudbuild/deploy-k8s.yml`  
脚本：`scripts/ci-deploy-k8s.sh`、`ops/remote-deploy.sh`

这张卡 SSH 到 ECS，上传已打好 Tag 的 `k8s/`，`kubectl apply -k`，等 rollout，失败则采集日志并回滚到上一成功版本。流水线仍显示失败。

控制台操作：

1. 新建任务 `NewSecondMall-deploy-k8s`，YAML 路径 `.cloudbuild/deploy-k8s.yml`。
2. 参数除 `codeBranch`、`PIPELINE_NUMBER` 外增加：

| 名称 | 说明 |
| --- | --- |
| `ECS_HOST` | 例如 `120.46.222.10` |
| `ECS_USER` | 例如 `root` |
| `ECS_SSH_PRIVATE_KEY` | 部署私钥，可勾选私密；平台会把 PEM 换行收成一行，脚本写入前会自动补回。不要把私钥发到聊天。平台注入文件时改用 `ECS_SSH_KEY_FILE` |
| `ECS_HOST_KEY` | 已核对的 known_hosts 完整行 |
| `HEALTHCHECK_BASE_URL` | 可选，默认 `http://127.0.0.1`（在 ECS 上探活） |
| `FAILURE_DEMO` | 可选，默认 `false`，禁止加入自动触发 |

3. 规格 `2U8G`。流水线新增阶段「部署到 Kubernetes」，依赖镜像制作。阶段不要并行。
4. 部署失败也会先上传 `ci-artifacts/**`，再由 `deploy-gate` 标红。

若步骤报没有 `ssh`，把该步骤完整日志发我。

## 健康检查（第 7 张卡片）

文件：`.cloudbuild/health.yml`  
脚本：`scripts/ci-k8s-health.sh`、`ops/remote-health.sh`

单独再查一次：mysql / backend / frontend / trade-service 均为 `1/1`，Deployment 注解里的 Tag / Commit / 流水线号，以及 `/` 与 `/api/products` 返回 HTTP 200。集群内 `GET http://trade-service:8083/api/cart` 应为 HTTP 401（未带登录 token）。

新建任务 `NewSecondMall-health`，YAML 路径 `.cloudbuild/health.yml`。参数与部署任务相同（`PIPELINE_NUMBER`、ECS SSH）。流水线放在部署后面，依赖部署阶段。

## 流水线 YAML 占位

`.codearts/workflow/pipeline.yml` 里：

- `REPLACE_WITH_BUILD_JOB_ID`：现有构建任务详情页 URL 末尾 32 位
- `REPLACE_WITH_UNIT_TEST_JOB_ID`：单元测试构建任务同样位置
- `REPLACE_WITH_INTEGRATION_API_JOB_ID`：集成与接口测试构建任务同样位置
- `REPLACE_WITH_E2E_JOB_ID`：端到端测试构建任务同样位置
- `REPLACE_WITH_PUBLISH_JOB_ID`：镜像制作构建任务同样位置
- `REPLACE_WITH_DEPLOY_JOB_ID`：Kubernetes 部署构建任务同样位置
- `REPLACE_WITH_HEALTH_JOB_ID`：健康检查构建任务同样位置

控制台选好任务后会自动填 `jobId`。不要手写，不要用 `official_git_clone`。

第一次接这三张卡时，从当前分支 **手工执行** 流水线。先不要给 `master` Push 开自动部署。部署阶段必须串行，避免两次发布同时改 ECS。
