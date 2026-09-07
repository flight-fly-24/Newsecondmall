-- =============================================================================
-- V001__init_schema.sql
-- 描述: 松果集市 - 基础建表（全部 20 张表 + collation 统一）
-- 引擎: InnoDB, 字符集 utf8mb4, collation utf8mb4_general_ci
-- 数据库: shop_db（自动创建）
-- 上游: shopping_back/shopping_back/doc/db.sql（同步源）
-- 注意事项:
--   1. 全部使用 CREATE TABLE IF NOT EXISTS，可重复执行
--   2. 末尾 ALTER ... CONVERT 是兜底，防止手动建表用了不同 collation
--   3. 末尾含 ensure_column 工具过程（兼容旧库升级），V001 调用一次后立即 DROP
-- =============================================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `shop_db`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `shop_db`;

-- ----------------------------------------------------------------------------
-- 用户
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
    `user_id` INT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `phone` VARCHAR(20) DEFAULT NULL,
    `credit` INT NOT NULL DEFAULT 100,
    `role` VARCHAR(20) NOT NULL DEFAULT 'buyer',
    `status` VARCHAR(20) NOT NULL DEFAULT 'normal',
    `avatar_url` VARCHAR(500) DEFAULT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

-- ----------------------------------------------------------------------------
-- 商品
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `goods` (
    `goods_id` INT NOT NULL AUTO_INCREMENT,
    `seller_id` INT NOT NULL,
    `goods_name` VARCHAR(255) NOT NULL,
    `category` VARCHAR(100) DEFAULT NULL,
    `goods_desc` TEXT,
    `goods_condition` VARCHAR(100) DEFAULT NULL,
    `story` TEXT,
    `price` DECIMAL(10, 2) NOT NULL DEFAULT 0,
    `floor_price` DECIMAL(10, 2) DEFAULT NULL,
    `scene` VARCHAR(20) NOT NULL DEFAULT 'used',
    `address` VARCHAR(255) DEFAULT NULL,
    `image` TEXT DEFAULT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'approved',
    `reject_reason` VARCHAR(255) DEFAULT NULL,
    `reviewed_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

-- ----------------------------------------------------------------------------
-- 实名认证
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_realname_auth` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `real_name` VARCHAR(50) NOT NULL,
    `id_card_masked` VARCHAR(30) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending',
    `reject_reason` VARCHAR(255) DEFAULT NULL,
    `reviewed_by` INT DEFAULT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `reviewed_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

-- ----------------------------------------------------------------------------
-- 收藏 / 浏览历史 / 关注店铺
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `favorite_goods` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `goods_id` INT NOT NULL,
    `item_title` VARCHAR(255) DEFAULT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `browse_history` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `goods_id` INT NOT NULL,
    `item_title` VARCHAR(255) DEFAULT NULL,
    `viewed_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `follow_store` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `store_id` INT NOT NULL,
    `store_name` VARCHAR(255) DEFAULT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `follow_topic` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `topic_id` INT NOT NULL,
    `topic_title` VARCHAR(255) DEFAULT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follow_user_topic` (`user_id`, `topic_id`),
    INDEX `idx_follow_topic_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

-- ----------------------------------------------------------------------------
-- 店铺 / 订单 / 评价 / 信用记录
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `store` (
    `store_id` INT NOT NULL AUTO_INCREMENT,
    `seller_id` INT NOT NULL,
    `store_name` VARCHAR(100) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'normal',
    `score` DECIMAL(3,1) NOT NULL DEFAULT 4.8,
    `credit_score` INT NOT NULL DEFAULT 100,
    `violation_count` INT NOT NULL DEFAULT 0,
    `store_desc` VARCHAR(500) DEFAULT NULL,
    `badge` VARCHAR(60) DEFAULT NULL,
    `service_tags` VARCHAR(255) DEFAULT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`store_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `orders` (
    `order_id` INT NOT NULL AUTO_INCREMENT,
    `buyer_id` INT NOT NULL,
    `seller_id` INT NOT NULL,
    `goods_id` INT DEFAULT NULL,
    `status` VARCHAR(30) NOT NULL,
    `amount` DECIMAL(10,2) NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`order_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `product_review` (
    `review_id` INT NOT NULL AUTO_INCREMENT,
    `order_id` INT NOT NULL,
    `goods_id` INT NOT NULL,
    `buyer_id` INT NOT NULL,
    `seller_id` INT NOT NULL,
    `product_score` INT NOT NULL,
    `seller_score` INT NOT NULL,
    `content` VARCHAR(1000) NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`review_id`),
    UNIQUE KEY `uk_review_order` (`order_id`),
    INDEX `idx_review_goods` (`goods_id`, `created_at`),
    INDEX `idx_review_seller` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `credit_record` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `change_value` INT NOT NULL,
    `reason` VARCHAR(255) NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

-- ----------------------------------------------------------------------------
-- 聊天
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_message` (
    `cm_id` INT NOT NULL AUTO_INCREMENT,
    `cov_id` INT NOT NULL,
    `sender_id` INT NOT NULL,
    `content` TEXT NOT NULL,
    `price_value` DECIMAL(10,2) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `is_read` TINYINT(1) DEFAULT 0,
    `type` VARCHAR(30) NOT NULL DEFAULT 'CHAT_MESSAGE',
    PRIMARY KEY (`cm_id`),
    INDEX `idx_cov_id_create_time` (`cov_id`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `conversation` (
    `cov_id` INT NOT NULL AUTO_INCREMENT,
    `buyer_id` INT NOT NULL,
    `seller_id` INT NOT NULL,
    `goods_id` INT NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending',
    `create_time` DATETIME NOT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`cov_id`),
    INDEX `idx_update_time` (`update_time` DESC),
    INDEX `idx_buyer_seller_goods` (`buyer_id`, `seller_id`, `goods_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

-- ----------------------------------------------------------------------------
-- 话题社区
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `community_topic` (
    `topic_id` INT NOT NULL AUTO_INCREMENT,
    `type` VARCHAR(60) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `topic_desc` VARCHAR(1000) NOT NULL,
    `author` VARCHAR(80) DEFAULT '松果社区',
    `cover` TEXT DEFAULT NULL,
    `tags` VARCHAR(500) DEFAULT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'normal',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `topic_post` (
    `post_id` INT NOT NULL AUTO_INCREMENT,
    `topic_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `product_id` INT DEFAULT NULL,
    `store_id` INT DEFAULT NULL,
    `content` TEXT NOT NULL,
    `images` TEXT DEFAULT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`post_id`),
    INDEX `idx_topic_post` (`topic_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `topic_comment` (
    `comment_id` INT NOT NULL AUTO_INCREMENT,
    `post_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `content` VARCHAR(1000) NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`comment_id`),
    INDEX `idx_post_comment` (`post_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `topic_post_like` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `post_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_topic_post_like` (`post_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `topic_post_action` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `post_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `action_type` VARCHAR(20) NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_topic_post_action` (`post_id`, `user_id`, `action_type`),
    INDEX `idx_topic_post_action` (`post_id`, `action_type`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

-- ----------------------------------------------------------------------------
-- 购物车 / 收货地址
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `cart_item` (
    `cart_id` INT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `goods_id` INT NOT NULL,
    `quantity` INT NOT NULL DEFAULT 1,
    `selected` TINYINT(1) NOT NULL DEFAULT 1,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`cart_id`),
    UNIQUE KEY `uk_cart_user_goods` (`user_id`, `goods_id`),
    INDEX `idx_cart_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `user_address` (
    `address_id` INT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `receiver` VARCHAR(50) NOT NULL,
    `phone` VARCHAR(20) NOT NULL,
    `province` VARCHAR(50) DEFAULT NULL,
    `city` VARCHAR(50) DEFAULT NULL,
    `district` VARCHAR(50) DEFAULT NULL,
    `detail` VARCHAR(255) NOT NULL,
    `is_default` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`address_id`),
    INDEX `idx_address_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE utf8mb4_general_ci;

-- ----------------------------------------------------------------------------
-- 兜底：把所有表转 utf8mb4_general_ci（兼容历史库）
-- ----------------------------------------------------------------------------
ALTER TABLE `users`                CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `goods`                CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `user_realname_auth`   CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `favorite_goods`       CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `browse_history`       CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `follow_store`         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `follow_topic`         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `store`                CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `orders`               CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `product_review`       CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `credit_record`        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `conversation`         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `chat_message`         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `community_topic`      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `topic_post`           CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `topic_comment`        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `topic_post_like`      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `topic_post_action`    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `cart_item`            CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `user_address`         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
