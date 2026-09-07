CREATE TABLE IF NOT EXISTS store (
    store_id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    seller_name VARCHAR(100) NOT NULL,
    store_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    score DECIMAL(3,1) NOT NULL DEFAULT 4.8,
    credit_score INT NOT NULL DEFAULT 100,
    store_desc VARCHAR(500),
    badge VARCHAR(60),
    service_tags VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (store_id), UNIQUE (seller_id)
);

CREATE TABLE IF NOT EXISTS goods (
    goods_id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    store_id BIGINT,
    store_name VARCHAR(100),
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100),
    description VARCHAR(2000),
    goods_condition VARCHAR(100),
    story VARCHAR(2000),
    price DECIMAL(10,2) NOT NULL,
    floor_price DECIMAL(10,2),
    scene VARCHAR(20) NOT NULL DEFAULT 'used',
    location VARCHAR(255),
    image VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    sold_order_number VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (goods_id)
);

CREATE TABLE IF NOT EXISTS favorite_goods (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, goods_id BIGINT NOT NULL,
    item_title VARCHAR(255), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE (user_id, goods_id)
);

CREATE TABLE IF NOT EXISTS browse_history (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, goods_id BIGINT NOT NULL,
    item_title VARCHAR(255), viewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS follow_store (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, store_id BIGINT NOT NULL,
    store_name VARCHAR(255), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE (user_id, store_id)
);

UPDATE store SET seller_name=CONCAT('卖家 ', seller_id) WHERE seller_name IS NULL OR seller_name='';

UPDATE goods g SET store_id=(SELECT MIN(s.store_id) FROM store s WHERE s.seller_id=g.seller_id)
WHERE store_id IS NULL;
UPDATE goods g SET store_name=(SELECT MIN(s.store_name) FROM store s WHERE s.seller_id=g.seller_id)
WHERE store_name IS NULL;
