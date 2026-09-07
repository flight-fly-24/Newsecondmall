# 购物与二手交易平台 - 本地环境配置

本项目当前包含：
- 后端：`shopping_back/shopping_back`（Spring Boot + MyBatis + MySQL）
- 前端：`shopping_front`（uni-app，Vue 3，HBuilderX 工程）

## 1. 必装软件

### 后端
- JDK 17（与 `pom.xml` 保持一致）
- Maven 3.9+（用IntelliJ IDEA等可以不装）
- MySQL 8.0+（或 openGauss，建议先跑通 MySQL）

### 前端
- HBuilderX（推荐，当前工程结构为 HBuilderX 标准 uni-app 工程）
- 微信开发者工具（用于小程序调试）

## 2. 数据库初始化（MySQL）

1) 登录 MySQL：

```sql
CREATE DATABASE IF NOT EXISTS shop_db
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;
```

2) 创建业务账号：

```sql
CREATE USER 'shop_user'@'localhost' IDENTIFIED BY 'shop_pass_123';
GRANT ALL PRIVILEGES ON shop_db.* TO 'shop_user'@'localhost';
FLUSH PRIVILEGES;
```

3) 执行数据库初始化脚本
数据库初始化脚本见 `shopping_back/shopping_back/doc/db.sql`
打开终端并登录你的 MySQL：
```bash
mysql -u shop_user -p
```
登录成功后，直接运行以下命令（请将路径替换为你本地的绝对路径）：
```sql
source /你的项目绝对路径/shopping_back/shopping_back/doc/db.sql;
```

## 3. 后端环境变量配置

后端已改为读取环境变量（见 `shopping_back/shopping_back/src/main/resources/application.properties`）：

- `DB_URL`（默认：`jdbc:mysql://localhost:3306/shop_db?...`）
- `DB_USERNAME`（默认：`shop_user`）
- `DB_PASSWORD`（默认：`shop_pass_123`，仅用于本地开发）

### Windows PowerShell（当前终端）示例

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/shop_db?serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME="shop_user"
$env:DB_PASSWORD="你的数据库密码"
```
注意：密码和 API Key 只能通过环境变量或未纳入版本控制的 `application-local.properties` 设置，不得提交到仓库。

## 4. 启动后端

进入目录：

```powershell
cd shopping_back/shopping_back
```

首次启动前先确认 Maven 可用：

```powershell
mvn -v
```

启动服务：

```powershell
mvn spring-boot:run
```

默认端口：`8080`（如未改动）

## 5. 启动前端（uni-app）

当前前端目录：项目根目录下的 `shopping_front/`

建议方式（HBuilderX）：
1. 打开 HBuilderX。
2. 导入项目目录 `shopping_front`。
3. 运行到浏览器（Web）或运行到微信开发者工具（小程序）。
4. 微信开发者工具运行与 AppID / API 配置详见 `docs/wechat-devtools.md`。

## 6. 前后端联调建议

- 前端请求统一走 **相对路径** `/api` 前缀（见 `shopping_front/utils/request.js`）。
- H5 开发：`shopping_front/manifest.json` 已配置 `h5.devServer.proxy`，将 `/api` 转发到 `http://127.0.0.1:8080`（请先启动后端）。
- 后端已提供全局 CORS（`CorsConfig`），可按 `application.properties` 中 `APP_CORS_PATTERNS` 收紧来源。
- **网站上线（输入域名即可访问）**：见 `docs/deploy.md`（H5 打包 + Nginx 静态目录 + `/api` 反代）。

## 7. 华为云 CodeArts 协作建议

- 仓库根目录建议增加：
  - `.gitignore`（过滤 `target/`、`unpackage/`、IDE 临时文件）
  - `docs/`（需求、接口、数据库脚本）
- 建议流水线拆分：
  - 后端：`mvn clean package`
  - 前端：HBuilderX 云打包或 CLI 构建（后续如切到 npm 版 uni-app）

## 8. 登录联调（内置体验账号）

后端已提供内存用户与 Token（详见 `docs/api.md`）：

- 用户名：`demo`
- 密码：`demo123`

在 H5 中打开 **我的 → 登录**，或使用 **注册** 创建新账号（数据在进程内存中，重启后端会丢失，仅用于开发阶段）。

## 9. 常见问题

- `mvn` 找不到：未安装 Maven 或未配置 `PATH`。
- 数据库连接失败：检查 `DB_URL/DB_USERNAME/DB_PASSWORD` 是否正确。
- 小程序无法请求本地接口：检查微信开发者工具是否已关闭域名校验（开发环境）。
