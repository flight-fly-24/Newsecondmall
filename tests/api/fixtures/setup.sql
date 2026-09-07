INSERT INTO users (username, password_hash, phone, credit, role, status)
SELECT 'ci_disabled', password_hash, '13999990001', 100, 'buyer', 'disabled'
FROM users
WHERE username = 'demo'
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    phone = VALUES(phone),
    role = VALUES(role),
    status = 'disabled';

DELETE FROM product_review WHERE order_id = 9003;
UPDATE orders SET status = 'completed' WHERE order_id = 9003;
