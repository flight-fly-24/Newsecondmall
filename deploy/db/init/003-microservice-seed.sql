-- =============================================================================
-- 003-microservice-seed.sql
-- 微服务库种子数据：catalog_db（商品/店铺）与 interaction_db（社区话题）
--
-- 背景：001/002 只初始化单体 shop_db；微服务由 spring.sql.init 自建表结构，
-- 但没有任何演示数据，导致线上首页商品列表、话题广场为空。
-- 本脚本与 002-seed.sql 数据保持一致，仅做列名映射（goods_name→name 等），
-- 全部幂等（INSERT ... ON DUPLICATE KEY UPDATE / INSERT IGNORE），可重复执行。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `catalog_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `interaction_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- ----------------------------------------------------------------------------
-- catalog_db.goods：与 002-seed.sql 相同的 8 件商品，状态置为 ON_SALE
-- ----------------------------------------------------------------------------
INSERT INTO `catalog_db`.`goods`
    (`goods_id`, `seller_id`, `name`, `category`, `description`, `goods_condition`, `story`, `price`, `floor_price`, `scene`, `location`, `image`, `status`)
VALUES
    (1001, 2, 'AirWave Pro 降噪耳机',     '数码影音',  '全新正品，45dB 主动降噪，38 小时续航，适合通勤、自习和线上会议。',         '全新',    '官方严选新品，支持一年质保和平台担保。',           699.00, 659.00, 'new',  '上海',         '/static/goods/airwave-pro.jpg',   'ON_SALE'),
    (1002, 2, '松果 Pad 11 学习平板',      '数码影音',  '11 英寸 2.5K 护眼屏，8GB+256GB，适合网课、笔记和轻办公。',                '全新',    '新品首发，赠保护套，适合开学季学习场景。',          2299.00, 2199.00, 'new',  '杭州',         '/static/goods/songuo-pad.jpg',    'ON_SALE'),
    (1003, 2, 'ViewTop 27 英寸 2K 显示器', '数码影音',  '二手 9 成新，无坏点，HDMI/DP 接口齐全，支持当面验货。',                  '9 成新',  '上一任主人用于设计作业和剪辑练习，屏幕状态稳定，现桌面升级转让。', 680.00, 620.00, 'used', '广州大学城',   '/static/goods/viewtop-monitor.jpg','ON_SALE'),
    (1004, 5, '软件工程导论与项目管理笔记', '学习资料',  '二手教材，含重点标注和课程项目复习提纲，适合期末复习。',                  '8.5 成新','上任主人用它完成软工课程项目，夹带需求评审清单和测试用例模板。', 18.00, 15.00, 'used', '武汉',         '/static/goods/software-book.jpg', 'ON_SALE'),
    (1005, 4, '人体工学椅 Pro',           '家居生活',  '二手 9 成新，腰托完整，坐垫回弹正常，适合宿舍或工位。',                   '9 成新',  '陪伴过多个项目冲刺，椅背和扶手状态良好，搬家出闲置。', 420.00, 380.00, 'used', '成都',         '/static/goods/ergo-chair.jpg',    'ON_SALE'),
    (1006, 4, '折叠护眼台灯',             '家居生活',  '全新护眼台灯，USB-C 供电，三档色温，宿舍桌面友好。',                     '全新',    '新品卖点围绕护眼、便携和收纳，适合夜间阅读。',      89.00, 79.00, 'new',  '深圳',         '/static/goods/desk-lamp.jpg',     'ON_SALE'),
    (1007, 2, '蓝牙机械键盘 K68',         '数码影音',  '全新 68 键蓝牙机械键盘，三模连接，适合宿舍桌搭。',                      '全新',    '新品现货，轻巧布局，兼顾平板和电脑输入。',         199.00, 179.00, 'new',  '上海',         '/static/goods/airwave-pro.jpg',   'ON_SALE'),
    (1008, 5, '高数复习讲义套装',          '图书文创',  '二手复习讲义，章节标注完整，附往年题型整理。',                          '8 成新', '学长考前整理资料，适合快速查漏补缺。',            26.00, 20.00, 'used', '武汉',         '/static/goods/software-book.jpg', 'ON_SALE')
ON DUPLICATE KEY UPDATE
    `seller_id`       = VALUES(`seller_id`),
    `name`            = VALUES(`name`),
    `category`        = VALUES(`category`),
    `description`     = VALUES(`description`),
    `goods_condition` = VALUES(`goods_condition`),
    `story`           = VALUES(`story`),
    `price`           = VALUES(`price`),
    `floor_price`     = VALUES(`floor_price`),
    `scene`           = VALUES(`scene`),
    `location`        = VALUES(`location`),
    `image`           = VALUES(`image`),
    `status`          = 'ON_SALE',
    `updated_at`      = CURRENT_TIMESTAMP;

-- 回填商品的店铺归属（与 catalog-service schema.sql 末尾的逻辑一致）
UPDATE `catalog_db`.`goods` g SET store_id=(SELECT MIN(s.store_id) FROM (SELECT store_id, seller_id FROM `catalog_db`.`store`) s WHERE s.seller_id=g.seller_id)
WHERE g.store_id IS NULL;
UPDATE `catalog_db`.`goods` g SET store_name=(SELECT MIN(s.store_name) FROM (SELECT store_id, store_name, seller_id FROM `catalog_db`.`store`) s WHERE s.seller_id=g.seller_id)
WHERE g.store_name IS NULL;

-- ----------------------------------------------------------------------------
-- interaction_db.community_topic / topic_post / topic_comment：与 002 相同的话题
-- ----------------------------------------------------------------------------
INSERT INTO `interaction_db`.`community_topic` (`topic_id`, `type`, `title`, `topic_desc`, `author`, `cover`, `tags`, `status`)
VALUES
    (7001, '新品推荐',     '开学新品推荐：哪些数码配件真正提升效率？', '围绕耳机、平板、键盘、台灯等新品，讨论真实使用体验、预算区间和避坑点。',       '松果编辑部',   '/static/goods/songuo-pad.jpg',   '新品推荐,数码影音,学生党,效率工具',  'normal'),
    (7002, '宿舍好物推荐', '宿舍好物推荐：桌面、收纳和二手小家具合集', '分享宿舍里真正用得上的好物，也欢迎晒出自己的桌搭和改造经验。',                 '宿舍改造小组', '/static/goods/ergo-chair.jpg',   '宿舍好物,家居生活,二手闲置,桌搭',    'normal'),
    (7003, '防晒避雷帖',   '防晒避雷帖：夏季通勤和军训怎么选才不踩坑？', '防晒、遮阳、清洁和晒后修护相关经验集中讨论，少花冤枉钱。',                     '生活经验社',   '/static/goods/desk-lamp.jpg',    '防晒避雷,生活经验,新品推荐,避坑',    'normal'),
    (7004, '学习资料合集', '期末和考研资料流转：哪些资料值得买二手？',   '教材、讲义、笔记和题集的购买经验与资料交换讨论。',                            '南湖旧书摊',   '/static/goods/software-book.jpg','学习资料,图书文创,二手闲置,期末复习', 'normal')
ON DUPLICATE KEY UPDATE
    `type`       = VALUES(`type`),
    `title`      = VALUES(`title`),
    `topic_desc` = VALUES(`topic_desc`),
    `author`     = VALUES(`author`),
    `cover`      = VALUES(`cover`),
    `tags`       = VALUES(`tags`),
    `status`     = VALUES(`status`);

INSERT INTO `interaction_db`.`topic_post` (`post_id`, `topic_id`, `user_id`, `product_id`, `store_id`, `content`, `images`, `author_name`)
VALUES
    (7201, 7001, 1, 1007, 1, '刚换了 68 键蓝牙键盘，宿舍桌面一下清爽很多。建议优先看连接稳定性和键帽高度，别只看颜值。',                '/static/goods/airwave-pro.jpg', '松果用户'),
    (7202, 7002, 1, 1005, 3, '二手人体工学椅真的要当面试坐，腰托和升降比外观更重要。我的经验是先问使用年限，再看底盘有没有异响。', '/static/goods/ergo-chair.jpg', '松果用户'),
    (7203, 7003, 1, NULL, NULL, '防晒别盲目囤大瓶，通勤和运动需求不一样。大家可以把空瓶体验和踩雷点发在这里，后面买的人少踩坑。',     '', '松果用户'),
    (7204, 7004, 1, 1004, 2, '软件工程课本如果带项目笔记会很值，单纯教材就看价格。买之前可以让卖家拍目录和重点页。',                 '/static/goods/software-book.jpg', '松果用户')
ON DUPLICATE KEY UPDATE
    `topic_id`   = VALUES(`topic_id`),
    `user_id`    = VALUES(`user_id`),
    `product_id` = VALUES(`product_id`),
    `store_id`   = VALUES(`store_id`),
    `content`    = VALUES(`content`),
    `images`     = VALUES(`images`);

INSERT INTO `interaction_db`.`topic_comment` (`comment_id`, `post_id`, `user_id`, `content`, `author_name`)
VALUES
    (7301, 7201, 2, '同意，三模切换稳定比灯效重要多了。', '松果卖家'),
    (7302, 7202, 5, '椅子还要看轮子，宿舍地面不平的话很影响体验。', '松果用户'),
    (7303, 7204, 2, '资料类最好让卖家说明有没有缺页和水渍。', '松果卖家')
ON DUPLICATE KEY UPDATE
    `post_id`  = VALUES(`post_id`),
    `user_id`  = VALUES(`user_id`),
    `content`  = VALUES(`content`);

INSERT IGNORE INTO `interaction_db`.`topic_post_like` (`post_id`, `user_id`)
VALUES
    (7201, 1),
    (7201, 2),
    (7202, 1),
    (7204, 1);

INSERT IGNORE INTO `interaction_db`.`topic_post_action` (`post_id`, `user_id`, `action_type`)
VALUES
    (7201, 2, 'want'),
    (7201, 1, 'collect'),
    (7202, 5, 'want'),
    (7204, 2, 'collect');
