CREATE TABLE IF NOT EXISTS orders (
    order_id BIGINT NOT NULL AUTO_INCREMENT,
    client_request_id VARCHAR(64) NOT NULL UNIQUE,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    compensation_attempts INT NOT NULL DEFAULT 0,
    shop_name VARCHAR(100),
    cover VARCHAR(1000),
    scene VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id)
);

CREATE TABLE IF NOT EXISTS cart_item (
    cart_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    goods_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    selected BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (cart_id),
    UNIQUE (user_id, goods_id)
);

CREATE TABLE IF NOT EXISTS product_review (
    review_id BIGINT NOT NULL AUTO_INCREMENT, order_id BIGINT NOT NULL UNIQUE, goods_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL, seller_id BIGINT NOT NULL, product_score INT NOT NULL,
    seller_score INT NOT NULL, content VARCHAR(1000) NOT NULL, PRIMARY KEY (review_id)
);
