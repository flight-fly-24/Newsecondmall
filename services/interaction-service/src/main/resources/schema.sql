CREATE TABLE IF NOT EXISTS community_topic (
    topic_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(60) NOT NULL DEFAULT '买家话题',
    title VARCHAR(255) NOT NULL,
    topic_desc VARCHAR(1000) NOT NULL DEFAULT '',
    author VARCHAR(80) DEFAULT '松果用户',
    cover TEXT,
    tags VARCHAR(500) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    creator_id BIGINT DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS topic_post (
    post_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    topic_id INT NOT NULL,
    user_id INT NOT NULL,
    product_id INT DEFAULT NULL,
    store_id INT DEFAULT NULL,
    content TEXT NOT NULL,
    images TEXT,
    author_name VARCHAR(80) DEFAULT NULL,
    author_avatar TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS topic_comment (
    comment_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    user_id INT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    author_name VARCHAR(80) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS topic_post_like (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    user_id INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (post_id, user_id)
);
CREATE TABLE IF NOT EXISTS topic_post_action (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    user_id INT NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (post_id, user_id, action_type)
);
CREATE TABLE IF NOT EXISTS follow_topic (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    topic_id INT NOT NULL,
    topic_title VARCHAR(255) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, topic_id)
);
CREATE TABLE IF NOT EXISTS conversation (
    conversation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    product_id BIGINT,
    status VARCHAR(20) NOT NULL
);
CREATE TABLE IF NOT EXISTS chat_message (
    message_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    message_type VARCHAR(20) NOT NULL
);
