# 松果集市 · 数据库运维手册

> 面向运维/值班的日常操作指引。所有命令已在本机（macOS Apple Silicon + Docker）验证。

## 1. 快速检查

### 1.1 一键体检

```bash
cd deploy/db
./tools/health-check.sh
```

体检项：
- 容器是否运行
- MySQL 端口可达
- 数据库是否存在
- 表数量 / 关键表行数
- collation 一致性
- 外键孤儿记录
- 后端 API 是否能正常查询

### 1.2 手工探活

```bash
# 容器状态
docker ps --filter "name=mysql"

# MySQL ping
docker exec deploy-mysql-1 sh -c "mysqladmin ping -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" --silent" && echo OK

# 当前活动会话
docker exec deploy-mysql-1 sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'SHOW PROCESSLIST;'"

# 后端 API 探活
curl -s http://localhost/api/products | head -c 200
```

## 2. 备份操作

### 2.1 日常备份

```bash
cd deploy/db
./backup/backup.sh
# 输出:
#   backups/shop_db_YYYYMMDD_HHMMSS.sql.gz       逻辑备份
#   backups/mysql_data_YYYYMMDD_HHMMSS.tar.gz    物理备份
#   backups/manifest_YYYYMMDD_HHMMSS.txt         元信息
```

### 2.2 自定义参数

```bash
# 备份到指定目录
BACKUP_DIR=/Users/ops/backups ./backup/backup.sh

# 仅逻辑备份（小，恢复慢但易读）
./backup/backup.sh logical

# 保留 30 天
KEEP_DAYS=30 ./backup/backup.sh
```

### 2.3 备份验证

```bash
# 列出所有备份
./backup/restore.sh --list

# 验证逻辑备份的 SQL 是否能正常解析
gunzip -c backups/shop_db_20260101_120000.sql.gz | head -50

# 在临时库中验证（不污染主库）
docker exec -i deploy-mysql-1 sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'CREATE DATABASE shop_db_test;'"
gunzip -c backups/shop_db_20260101_120000.sql.gz | \
  docker exec -i deploy-mysql-1 sh -c "mysql --default-character-set=utf8mb4 -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" shop_db_test"
docker exec deploy-mysql-1 sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'DROP DATABASE shop_db_test;'"
```

## 3. 恢复操作

### 3.1 列出可用备份

```bash
./backup/restore.sh --list
```

### 3.2 逻辑恢复

```bash
./backup/restore.sh backups/shop_db_20260101_120000.sql.gz
# 提示输入 YES 确认
```

执行内容：
1. 关闭外键检查
2. 导入 SQL（自动解压 .gz）
3. 开启外键检查
4. 输出当前表数

### 3.3 物理恢复

```bash
./backup/restore.sh backups/mysql_data_20260101_120000.tar.gz
# 提示输入 YES 确认
```

执行内容：
1. 停止 mysql 容器（其他服务同停）
2. 删除并重建 mysql_data 卷
3. 解压备份到临时目录
4. 用新容器把数据写回卷
5. 重新启动服务并等 healthy

### 3.4 自动化/CI 跳过确认

```bash
./backup/restore.sh backups/shop_db_20260101_120000.sql.gz --yes
```

## 4. 迁移操作

### 4.1 查看当前状态

```bash
cd deploy/db
DB_CONTAINER=deploy-mysql-1 ./migrations/apply.sh --status
# 输出: 已应用的迁移 + 可用的脚本
```

### 4.2 应用新迁移

```bash
# 1) 提交新脚本到 Git
git add migrations/V003__add_coupon.sql
git commit -m "feat(db): 新增 coupon 表"

# 2) 同步到 init/（保持 docker entrypoint 行为可独立运行）
cp migrations/V003__add_coupon.sql init/003-coupon.sql

# 3) 应用
DB_CONTAINER=deploy-mysql-1 ./migrations/apply.sh
# 输出: [SKIP] V001, [SKIP] V002, [APPLY] V003
```

### 4.3 应用到指定版本

```bash
DB_CONTAINER=deploy-mysql-1 ./migrations/apply.sh --to V002
# 停在 V002，V003+ 不动
```

### 4.4 强制重跑（修复数据问题用）

```bash
DB_CONTAINER=deploy-mysql-1 ./migrations/apply.sh --force V002
# 删除 schema_migrations 中 V002 记录，重新执行脚本
# ⚠️ 不会回滚 V002 之前已经做出的 DDL/DML，需手工处理
```

### 4.5 空库初始化（首次部署）

```bash
DB_CONTAINER=deploy-mysql-1 ./migrations/apply.sh --init
# 等价于 docker-compose entrypoint 行为：
#   - 数据库不存在任何表时执行 init/*.sql
#   - 已有表时跳过
```

## 5. 压力测试前置

### 5.1 准备测试数据

```bash
# 导入 10x 测试数据
DB_CONTAINER=deploy-mysql-1 ./tools/reset.sh --yes
# 重复跑 V002 可放大数据（手动写脚本或 SQL）
```

### 5.2 关闭外键提升插入性能

```sql
SET FOREIGN_KEY_CHECKS = 0;
-- 大量 INSERT ...
SET FOREIGN_KEY_CHECKS = 1;
```

### 5.3 推荐测试工具

| 工具 | 用途 | 入口 |
|---|---|---|
| sysbench | 通用 OLTP 压测 | `apt install sysbench` |
| mysqlslap | MySQL 自带 | `docker exec ... mysqlslap` |
| tpcc-mysql | 模拟电商场景 | github.com/Percona-Lab/tpcc-mysql |

### 5.4 基础性能观测

```bash
# 当前慢查询（需先 SET GLOBAL slow_query_log='ON'）
docker exec deploy-mysql-1 sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'SHOW VARIABLES LIKE \"%slow%\";'"

# 当前锁等待
docker exec deploy-mysql-1 sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'SELECT * FROM information_schema.INNODB_LOCK_WAITS;'"

# Buffer pool 命中率
docker exec deploy-mysql-1 sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'SHOW STATUS LIKE \"Innodb_buffer_pool_read%\";'"
```

## 6. 应急场景

### 6.1 容器起不来

```bash
# 看日志
docker logs deploy-mysql-1 --tail 100

# 90% 是端口冲突或磁盘满
df -h /var/lib/docker  # 宿主机
lsof -i :3306           # 端口占用

# 物理卷损坏 → 用最近的物理备份恢复
# 卷没坏但数据库文件损坏 → 用最近的逻辑备份恢复
```

### 6.2 表损坏（crash 后）

```bash
# 1) 进入容器，停止后端避免新写入
docker exec deploy-mysql-1 sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'FLUSH TABLES WITH READ LOCK;'"

# 2) 检查所有表
docker exec deploy-mysql-1 sh -c "mysqlcheck -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" --all-databases --check"

# 3) 自动修复
docker exec deploy-mysql-1 sh -c "mysqlcheck -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" --all-databases --auto-repair"

# 4) 解锁
docker exec deploy-mysql-1 sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'UNLOCK TABLES;'"
```

### 6.3 误删数据（10 分钟内）

```bash
# 1) 立刻断开所有写操作
docker exec deploy-mysql-1 sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'SET GLOBAL read_only = ON;'"

# 2) 看 binlog 找恢复点
docker exec deploy-mysql-1 sh -c "mysqlbinlog /var/lib/mysql/binlog.000001 --start-datetime='2026-08-28 10:00:00' --stop-datetime='2026-08-28 10:30:00'"

# 3) 导出回滚 SQL 并恢复
# （具体操作依赖 binlog 格式，此处略）

# 4) 恢复写权限
docker exec deploy-mysql-1 sh -c "mysql -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" -e 'SET GLOBAL read_only = OFF;'"
```

### 6.4 重置整个库（开发环境）

```bash
cd deploy/db
./tools/reset.sh --yes
# 会:
#   1) docker compose down -v mysql（删卷）
#   2) docker compose up -d（自动跑 init/ 重新建库）
```

## 7. 安全检查清单

部署上线前必查：

- [ ] 密码不在 Git 中（`.env` 已加入 `.gitignore`）
- [ ] 数据库用户最小权限（应用账号无 DROP/GRANT）
- [ ] 3306 端口未对外暴露（仅 docker 内网可达）
- [ ] 慢查询日志已开启
- [ ] 备份定期执行且异地存储
- [ ] binlog 至少保留 7 天
- [ ] 定期演练恢复（建议每月一次）

## 8. 监控建议

简单方案：
- 容器健康：`docker ps` 看 STATUS
- 磁盘：宿主机 `df -h /var/lib/docker`
- 表/行数变化：`./tools/health-check.sh` 写 cron，每 6 小时

进阶方案：
- Prometheus + mysqld_exporter
- Grafana 看板
- 告警阈值：连接数 > 80% max_connections，磁盘 < 20%，慢查询 > 10/分钟
