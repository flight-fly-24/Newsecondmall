# 松果集市 · 数据库可迁移交付（戴坤廷）

> 数据库可迁移交付包：建表、迁移、测试数据、空库初始化、备份恢复、运维文档。
> 适用 MySQL 8.0+，InnoDB，字符集 `utf8mb4`，collation `utf8mb4_general_ci`。

## 目录结构

```
deploy/db/
├── README.md                ← 本文档
├── migrations/              ← 版本化迁移（权威源）
│   ├── V001__init_schema.sql
│   ├── V002__seed_data.sql
│   └── apply.sh             ← 迁移工具（状态/应用/强制重跑/空库初始化）
├── init/                    ← Docker entrypoint 用（与 migrations 内容一致）
│   ├── 001-schema.sql
│   └── 002-seed.sql
├── backup/
│   ├── backup.sh            ← 一键备份（逻辑 + 物理 + 清单 + 自动清理）
│   └── restore.sh           ← 一键恢复（自动识别类型 + 二次确认）
├── tools/
│   ├── health-check.sh      ← 容器/库/表/行数/连接 全面体检
│   └── reset.sh             ← 开发用：删库重建（⚠️ 危险）
└── docs/
    └── OPERATIONS.md        ← 运维手册
```

## 一、运行架构

```
docker-compose 启动
  ↓
mysql 容器首次启动 → 读 /docker-entrypoint-initdb.d/*.sql（= init/）
  ↓
[空库]   自动执行 init/001-schema.sql + init/002-seed.sql
[非空库] 自动跳过（entrypoint 行为）
  ↓
启动顺序：mysql healthy → backend → frontend
```

## 二、一键命令速查

```bash
# 1) 部署整套（含空库初始化）
cd deploy
docker compose --env-file .env -f docker-compose.yml up -d --build

# 2) 查看数据库迁移状态
DB_CONTAINER=deploy-mysql-1 ./migrations/apply.sh --status

# 3) 增量应用迁移（仅对未执行脚本生效）
DB_CONTAINER=deploy-mysql-1 ./migrations/apply.sh

# 4) 备份（逻辑+物理，写 manifest，自动清 7 天前）
./backup/backup.sh

# 5) 备份（仅逻辑/仅物理）
./backup/backup.sh logical
./backup/backup.sh physical

# 6) 列出可用备份
./backup/restore.sh --list

# 7) 恢复（会要求输入 YES 确认）
./backup/restore.sh backups/shop_db_20260101_120000.sql.gz
./backup/restore.sh backups/mysql_data_20260101_120000.tar.gz

# 8) 体检
./tools/health-check.sh

# 9) 重置（⚠️ 删除所有数据，重新初始化）
./tools/reset.sh --yes
```

## 三、迁移版本约定

- 文件名：`V<n>__<description>.sql`（双下划线，Flyway 风格）
- 版本号：`V001`、`V002`、`V003` …（左 0 填充三位数）
- 命名空间唯一：版本号 + 描述在同一项目中唯一
- 元数据表：`schema_migrations`（`version`, `description`, `applied_at`, `checksum`）
- 幂等性：每条 DDL/DML 必须可重复执行（`IF NOT EXISTS` / `ON DUPLICATE KEY`）

### 新增迁移的标准流程

1. 在 `migrations/` 下新建 `V00X__xxx.sql`
2. 同步一份到 `init/00X-xxx.sql`（保持 docker entrypoint 行为可独立运行）
3. 跑 `./migrations/apply.sh` 应用
4. 提交到 Git（在 `fix/db-migration-V00X` 分支上做 review）

## 四、备份策略

| 备份类型 | 内容 | 大小 | 恢复速度 | 用途 |
|---|---|---|---|---|
| 逻辑（mysqldump） | SQL 语句 | 中 | 慢 | 跨版本迁移、调试、可读性高 |
| 物理（卷拷贝） | InnoDB 文件 | 大 | 快 | 大库、整库灾难恢复 |
| 清单 manifest | 元信息 | 极小 | – | 记录版本/大小/恢复命令 |

**建议**：
- 开发：每周一次逻辑备份即可
- 生产：每日一次逻辑备份 + 每周一次物理备份，异地存储

**保留策略**：默认 7 天，可在 `backup.sh` 开头用 `KEEP_DAYS=N` 覆盖

## 五、默认账号

| 账号 | 角色 | 密码 |
|---|---|---|
| demo | 买家 (buyer) | demo123 |
| seller | 卖家 (seller) | seller123 |
| admin | 管理员 (admin) | admin123 |
| life_seller | 卖家 (seller) | seller123 |
| book_seller | 卖家 (seller) | seller123 |

> 密码哈希为 BCrypt。如需修改，请改 `migrations/V002__seed_data.sql` 并提交新版本迁移。

## 六、与 docker-compose 的配合

`deploy/docker-compose.yml` 当前挂载的是：
```yaml
volumes:
  - ../shopping_back/shopping_back/doc/db.sql:/docker-entrypoint-initdb.d/001-schema.sql:ro
```

**建议切换到**（戴坤廷交付物落盘后）：
```yaml
volumes:
  - ./db/init:/docker-entrypoint-initdb.d:ro
```

切换好处：
- 单一权威源（`migrations/`）
- 与运维工具链一致
- 便于代码 review

## 七、与代码仓的同步

- 主开发库脚本：`shopping_back/shopping_back/doc/db.sql`（兼容老部署用）
- 运维交付脚本：`deploy/db/migrations/V*.sql`（**新规范**）
- 同步关系：每次 `db.sql` 变更必须同步提交到 `deploy/db/migrations/` + `deploy/db/init/`

## 八、与 K8s 部署集成

K8s 自动部署链路上的数据库集成点（戴坤廷交付）：

| 集成点 | 关系 | 文件 |
|---|---|---|
| `k8s/mysql-init-configmap.yaml` | 从 `deploy/db/init/` 自动生成 | `deploy/db/tools/build-k8s-configmap.sh` |
| K8s MySQL Deployment 挂载 | `mysql.yaml` 引用 `mysql-init` ConfigMap 到 `/docker-entrypoint-initdb.d` | `k8s/mysql.yaml` |
| 部署后健康检查 | `ops/remote-deploy.sh` 调 `health-check.sh --quick` | `ops/remote-deploy.sh` |

### 重新生成 ConfigMap（schema 变更后必跑）

```bash
cd deploy/db
./tools/build-k8s-configmap.sh --out ../k8s/mysql-init-configmap.yaml
git add ../k8s/mysql-init-configmap.yaml
git commit -m "chore(k8s): regenerate mysql-init from deploy/db/init"
```

### K8s 部署后健康检查

`ops/remote-deploy.sh` 的 `check_rollout_and_health()` 会在所有 Deployment rollout 通过后，
自动用 kubectl 取到 MySQL Pod 名，再 exec 跑 `health-check.sh --quick`，覆盖：
- 容器 ping / db 可达 / 表数
- collation 一致
- 关键唯一键
- 外键孤儿记录
- 默认账号存在
- 后端 API 可达

任一项失败则回滚到上一版本。

## 九、详细操作

- 详细运维命令：见 `docs/OPERATIONS.md`
- 压力测试前置条件：见 `docs/OPERATIONS.md` 第 5 节

## 十、变更记录

| 日期 | 变更 | 作者 |
|---|---|---|
| 2026-08-28 | 初始化 deploy/db/，拆分 V001/V002，提供迁移/备份/恢复/文档 | 戴坤廷 |
| 2026-08-28 | 适配 master：加 build-k8s-configmap.sh + 重生成 K8s ConfigMap + 部署后 health-check 集成 | 戴坤廷 |
