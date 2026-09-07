# Kubernetes 部署说明

当前环境使用华为云 SWR（华北-北京四）保存前后端镜像，使用 ECS 上的 k3s 运行 MySQL、后端和前端。

## 目录与组件

- `k8s/`：Namespace、ConfigMap、MySQL、后端、前端、Ingress 和 Kustomize 配置。
- `ops/services.conf`：发布服务清单；后续增加微服务时在这里登记。
- `ops/push-swr.ps1`：Windows 人工构建和推送单个镜像。
- `scripts/ci-publish-images.sh` / `scripts/ci-deploy-k8s.sh` / `scripts/ci-k8s-health.sh`：本地或 Linux CI 入口；CodeArts 对应三张卡。
- `ops/remote-deploy.sh`：ECS 上执行 rollout、健康检查、诊断和回滚。
- `ops/remote-health.sh`：ECS 上独立健康检查。

仓库中不得保存华为云密码、AK/SK、SWR Token、数据库口令、SSH 私钥或 ECS 密码。

## 首次准备

1. 在华北-北京四的 SWR `songguo` 组织中确认存在 `shop-backend`、`shop-frontend` 和 `trade-service`。
2. ECS 安装 k3s，并确认 `kubectl`、`flock`、`curl` 可用。
3. 应用 `k8s/namespace.yaml`，创建 `shop` 命名空间。
4. 通过安全渠道在 ECS 创建 Secret，不要生成或提交包含真实值的 YAML：

```bash
sudo kubectl -n shop create secret generic shop-db \
  --from-literal=DB_USERNAME='<数据库用户>' \
  --from-literal=DB_PASSWORD='<数据库密码>' \
  --from-literal=MYSQL_ROOT_PASSWORD='<root密码>'

sudo kubectl -n shop create secret docker-registry swr-secret \
  --docker-server=swr.cn-north-4.myhuaweicloud.com \
  --docker-username='<SWR用户名>' \
  --docker-password='<SWR凭据>'
```

命令中的占位内容应由负责人现场输入。Shell 历史可能记录参数，正式环境优先使用受控 Secret 管理工具；完成后及时清理历史并轮换临时凭据。

5. 给 CodeArts 创建专用 SSH 密钥，将公钥加入 ECS 的 `authorized_keys`，私钥只保存到 CodeArts 加密凭据。
6. 将 `ssh-keyscan` 得到的 ECS 主机公钥与控制台指纹核对后，完整保存为 CodeArts 的 `ECS_HOST_KEY`。

## 人工发布

先使用 SWR 控制台新生成的登录命令登录，Tag 必须以 `release-` 开头且不能重复：

```powershell
$tag = "release-manual-20260828-1030"
.\ops\push-swr.ps1 $tag backend
.\ops\push-swr.ps1 $tag frontend
```

脚本会构建 `linux/amd64` 镜像、推送 SWR，并更新本地 `k8s/kustomization.yaml`。随后将整个 `k8s/` 同步到 ECS，再执行：

```bash
sudo kubectl apply -k k8s/
sudo kubectl -n shop rollout status deployment/backend --timeout=300s
sudo kubectl -n shop rollout status deployment/frontend --timeout=300s
```

## 验证版本与健康状态

```bash
sudo kubectl -n shop get pods
sudo kubectl -n shop get deployment backend frontend \
  -o custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image,VERSION:.metadata.annotations.songguo\\.dev/image-tag
curl -fsS http://127.0.0.1/
curl -fsS http://127.0.0.1/api/products
```

## 自动发布与回滚

CodeArts 使用 `.cloudbuild/publish-images.yml`、`.cloudbuild/deploy-k8s.yml`、`.cloudbuild/health.yml` 三张串行卡片。每次成功发布保存在 `/opt/soft-shop/releases/<IMAGE_TAG>`，`current` 指向最近成功版本，默认保留最近 10 个版本。

失败时脚本先保存 Deployment 描述、Events 和 Pod 日志，再重新应用 `current` 指向的上一成功版本。回滚成功后，本次流水线仍保持失败状态，便于保留真实失败记录。

手工回滚到指定版本：

```bash
sudo kubectl apply -k /opt/soft-shop/releases/<旧版本>/k8s
sudo kubectl -n shop rollout status deployment/backend --timeout=300s
sudo kubectl -n shop rollout status deployment/frontend --timeout=300s
```

## 安全要求

- 禁止使用或覆盖 `latest`。
- 禁止将凭据写入 Git、README、流水线 YAML 或发布产物。
- 曾经出现在仓库或聊天中的 SWR/ECS 凭据必须立即撤销并轮换。
- 当前先手工触发功能分支；确认后再把正式流水线改为只监听 `master`。功能分支仅运行构建和测试时，不要打开自动部署。
