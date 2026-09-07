-- user-service 数据库初始化
-- 仅建 users 表（含 password_hash / avatar_url）
-- user_address / user_realname_auth / credit_record / favorite_goods / browse_history /
-- follow_store / follow_topic / store / orders 等表由各 Service 的 ensureSchema() / createTable() 自建

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL DEFAULT 'buyer',
    credit INT NOT NULL DEFAULT 100,
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    avatar_url VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
);
