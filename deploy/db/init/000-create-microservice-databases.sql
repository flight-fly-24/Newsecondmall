-- 微服务数据库初始化：创建 4 个业务库并授权给应用账号。
-- 注意：这里的 'shop_user' 用户名需与 deploy/.env 中的 DB_USERNAME 保持一致。
-- 各库的建表由对应微服务启动时通过 spring.sql.init 自动执行 schema.sql 完成，
-- 本脚本只负责「建库 + 授权」，不建表。
CREATE DATABASE IF NOT EXISTS user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS catalog_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS trade_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS interaction_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

GRANT ALL PRIVILEGES ON user_db.* TO 'shop_user'@'%';
GRANT ALL PRIVILEGES ON catalog_db.* TO 'shop_user'@'%';
GRANT ALL PRIVILEGES ON trade_db.* TO 'shop_user'@'%';
GRANT ALL PRIVILEGES ON interaction_db.* TO 'shop_user'@'%';
FLUSH PRIVILEGES;
