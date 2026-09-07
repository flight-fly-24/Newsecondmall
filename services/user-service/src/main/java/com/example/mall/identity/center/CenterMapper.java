package com.example.mall.identity.center;

import com.example.mall.identity.center.CenterDtos.InteractionItem;
import com.example.mall.identity.center.CenterDtos.RealNameView;
import com.example.mall.identity.center.CenterDtos.UserRow;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CenterMapper {
    @Update("CREATE TABLE IF NOT EXISTS user_realname_auth (" +
            "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
            "user_id INT NOT NULL, real_name VARCHAR(50) NOT NULL, id_card_masked VARCHAR(30) NOT NULL, " +
            "status VARCHAR(20) NOT NULL DEFAULT 'pending', reject_reason VARCHAR(255) DEFAULT NULL, " +
            "reviewed_by INT DEFAULT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, reviewed_at TIMESTAMP NULL DEFAULT NULL)")
    void createRealNameTable();

    @Update("CREATE TABLE IF NOT EXISTS favorite_goods (" +
            "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, goods_id INT NOT NULL, item_title VARCHAR(255) DEFAULT NULL, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")
    void createFavoriteTable();

    @Update("CREATE TABLE IF NOT EXISTS browse_history (" +
            "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, goods_id INT NOT NULL, item_title VARCHAR(255) DEFAULT NULL, " +
            "viewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")
    void createBrowseTable();

    @Update("CREATE TABLE IF NOT EXISTS follow_store (" +
            "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, store_id INT NOT NULL, store_name VARCHAR(255) DEFAULT NULL, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")
    void createFollowTable();

    @Update("CREATE TABLE IF NOT EXISTS follow_topic (" +
            "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, topic_id INT NOT NULL, topic_title VARCHAR(255) DEFAULT NULL, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_follow_user_topic (user_id, topic_id))")
    void createFollowTopicTable();

    @Update("CREATE TABLE IF NOT EXISTS store (" +
            "store_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, seller_id INT NOT NULL, store_name VARCHAR(100) NOT NULL, " +
            "status VARCHAR(20) NOT NULL DEFAULT 'normal', score DECIMAL(3,1) NOT NULL DEFAULT 4.8, " +
            "credit_score INT NOT NULL DEFAULT 100, violation_count INT NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")
    void createStoreTable();

    @Update("CREATE TABLE IF NOT EXISTS orders (" +
            "order_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, buyer_id INT NOT NULL, seller_id INT NOT NULL, " +
            "goods_id INT DEFAULT NULL, status VARCHAR(30) NOT NULL, amount DECIMAL(10,2) NOT NULL DEFAULT 0, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")
    void createOrdersTable();

    @Update("CREATE TABLE IF NOT EXISTS goods (" +
            "goods_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, seller_id INT NOT NULL, " +
            "goods_name VARCHAR(255) NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'pending')")
    void createGoodsCompatibilityTable();

    @Update("CREATE TABLE IF NOT EXISTS credit_record (" +
            "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, change_value INT NOT NULL, " +
            "reason VARCHAR(255) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")
    void createCreditRecordTable();

    @Select("SELECT COUNT(*) FROM favorite_goods WHERE user_id = #{userId}")
    int favoriteCount(@Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM browse_history WHERE user_id = #{userId}")
    int browseCount(@Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM follow_store WHERE user_id = #{userId}")
    int followCount(@Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM follow_topic WHERE user_id = #{userId}")
    int followTopicCount(@Param("userId") Integer userId);

    @Select("SELECT reason FROM credit_record WHERE user_id = #{userId} ORDER BY id DESC LIMIT 1")
    String latestCreditReason(@Param("userId") Integer userId);

    @Select("SELECT id, user_id AS userId, '' AS username, CONCAT(SUBSTRING(real_name, 1, 1), '*') AS realName, id_card_masked AS idCardMasked, status, reject_reason AS rejectReason " +
            "FROM user_realname_auth WHERE user_id = #{userId} ORDER BY id DESC LIMIT 1")
    RealNameView latestRealName(@Param("userId") Integer userId);

    @Insert("INSERT INTO user_realname_auth(user_id, real_name, id_card_masked, status) VALUES(#{userId}, #{realName}, #{idCardMasked}, 'pending')")
    int insertRealName(@Param("userId") Integer userId, @Param("realName") String realName, @Param("idCardMasked") String idCardMasked);

    @Delete("DELETE FROM user_realname_auth WHERE user_id = #{userId}")
    int deleteRealNameByUserId(@Param("userId") Integer userId);

    @Select("SELECT store_name FROM store WHERE seller_id = #{sellerId} ORDER BY store_id DESC LIMIT 1")
    String storeName(@Param("sellerId") Integer sellerId);

    @Select("SELECT status FROM store WHERE seller_id = #{sellerId} ORDER BY store_id DESC LIMIT 1")
    String storeStatus(@Param("sellerId") Integer sellerId);

    @Select("SELECT COUNT(*) FROM goods WHERE seller_id = #{sellerId} AND status = #{status}")
    int sellerGoodsCount(@Param("sellerId") Integer sellerId, @Param("status") String status);

    @Select("SELECT COUNT(*) FROM orders WHERE seller_id = #{sellerId} AND status = #{status}")
    int sellerOrderCount(@Param("sellerId") Integer sellerId, @Param("status") String status);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM orders WHERE seller_id = #{sellerId}")
    BigDecimal sellerAmount(@Param("sellerId") Integer sellerId);

    @Select("SELECT credit_score FROM store WHERE seller_id = #{sellerId} ORDER BY store_id DESC LIMIT 1")
    Integer storeCredit(@Param("sellerId") Integer sellerId);

    @Select("SELECT violation_count FROM store WHERE seller_id = #{sellerId} ORDER BY store_id DESC LIMIT 1")
    Integer violationCount(@Param("sellerId") Integer sellerId);

    @Select("SELECT COUNT(*) FROM users")
    int totalUsers();

    @Select("SELECT COUNT(*) FROM users WHERE role = #{role}")
    int roleCount(@Param("role") String role);

    @Select("SELECT COUNT(*) FROM users WHERE status = 'disabled'")
    int disabledUsers();

    @Select("SELECT COUNT(*) FROM goods")
    int totalGoods();

    @Select("SELECT COUNT(*) FROM goods WHERE status IN ('2', 'pending', 'pe')")
    int pendingGoods();

    @Select("SELECT COUNT(*) FROM store WHERE status = 'pending'")
    int pendingStores();

    @Select("SELECT COUNT(*) FROM user_realname_auth WHERE status = 'pending'")
    int pendingRealName();

    @Select("SELECT user_id AS userId, username, role, COALESCE(status, 'normal') AS status, credit FROM users ORDER BY user_id LIMIT 20")
    List<UserRow> users();

    @Select("SELECT role FROM users WHERE user_id = #{userId}")
    String userRole(@Param("userId") Integer userId);

    @Select("SELECT username FROM users WHERE user_id = #{userId}")
    String usernameById(@Param("userId") Integer userId);

    @Select("SELECT a.id, a.user_id AS userId, u.username, a.real_name AS realName, a.id_card_masked AS idCardMasked, " +
            "a.status, a.reject_reason AS rejectReason FROM user_realname_auth a JOIN users u ON u.user_id = a.user_id " +
            "WHERE a.status = 'pending' ORDER BY a.id DESC LIMIT 20")
    List<RealNameView> pendingRealNames();

    @Update("UPDATE users SET status = #{status} WHERE user_id = #{userId}")
    int updateUserStatus(@Param("userId") Integer userId, @Param("status") String status);

    @Delete("DELETE FROM users WHERE user_id = #{userId}")
    int deleteUser(@Param("userId") Integer userId);

    @Delete("DELETE FROM credit_record WHERE user_id = #{userId}")
    int deleteCreditRecords(@Param("userId") Integer userId);

    @Delete("DELETE FROM orders WHERE buyer_id = #{userId} OR seller_id = #{userId}")
    int deleteOrdersByUserId(@Param("userId") Integer userId);

    @Delete("DELETE FROM store WHERE seller_id = #{sellerId}")
    int deleteStoresBySellerId(@Param("sellerId") Integer sellerId);

    @Delete("DELETE FROM goods WHERE seller_id = #{sellerId}")
    int deleteGoodsBySellerId(@Param("sellerId") Integer sellerId);

    @Update("UPDATE user_realname_auth SET status = 'approved', reject_reason = NULL, reviewed_by = #{adminId}, reviewed_at = NOW() WHERE id = #{id}")
    int approveRealName(@Param("id") Integer id, @Param("adminId") Integer adminId);

    @Update("UPDATE user_realname_auth SET status = 'rejected', reject_reason = #{reason}, reviewed_by = #{adminId}, reviewed_at = NOW() WHERE id = #{id}")
    int rejectRealName(@Param("id") Integer id, @Param("adminId") Integer adminId, @Param("reason") String reason);

    @Select("SELECT COUNT(*) FROM store WHERE seller_id = #{sellerId}")
    int sellerStoreCount(@Param("sellerId") Integer sellerId);

    @Insert("INSERT INTO store(seller_id, store_name, status, score, credit_score, violation_count) " +
            "VALUES(#{sellerId}, #{storeName}, 'normal', 4.8, 100, 0)")
    int insertStore(@Param("sellerId") Integer sellerId, @Param("storeName") String storeName);

    @Select("SELECT COUNT(*) FROM credit_record WHERE user_id = #{userId}")
    int creditRecordCount(@Param("userId") Integer userId);

    @Insert("INSERT INTO credit_record(user_id, change_value, reason) VALUES(#{userId}, 0, #{reason})")
    int insertCreditRecord(@Param("userId") Integer userId, @Param("reason") String reason);

    @Insert("INSERT INTO credit_record(user_id, change_value, reason) VALUES(#{userId}, #{changeValue}, #{reason})")
    int insertCreditChange(@Param("userId") Integer userId, @Param("changeValue") Integer changeValue, @Param("reason") String reason);

    @Update("UPDATE users SET credit = GREATEST(0, LEAST(100, credit + #{changeValue})) WHERE user_id = #{userId}")
    int updateCredit(@Param("userId") Integer userId, @Param("changeValue") Integer changeValue);

    @Select("SELECT id, reason AS title, CONCAT('变动 ', change_value, ' 分') AS `desc`, 'credit' AS type, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS createdAt, NULL AS targetId " +
            "FROM credit_record WHERE user_id = #{userId} ORDER BY id DESC LIMIT 20")
    List<InteractionItem> creditRecords(@Param("userId") Integer userId);

    @Insert("INSERT INTO favorite_goods(user_id, goods_id, item_title) VALUES(#{userId}, #{goodsId}, #{title})")
    int addFavorite(@Param("userId") Integer userId, @Param("goodsId") Integer goodsId, @Param("title") String title);

    @Insert("INSERT INTO browse_history(user_id, goods_id, item_title) VALUES(#{userId}, #{goodsId}, #{title})")
    int addBrowse(@Param("userId") Integer userId, @Param("goodsId") Integer goodsId, @Param("title") String title);

    @Insert("INSERT IGNORE INTO follow_store(user_id, store_id, store_name) " +
            "SELECT #{userId}, store_id, store_name FROM store WHERE store_name = #{storeName} ORDER BY store_id LIMIT 1")
    int addFollow(@Param("userId") Integer userId, @Param("storeName") String storeName);

    @Delete("DELETE FROM favorite_goods WHERE user_id = #{userId}")
    int clearFavorites(@Param("userId") Integer userId);

    @Delete("DELETE FROM browse_history WHERE user_id = #{userId}")
    int clearBrowse(@Param("userId") Integer userId);

    @Delete("DELETE FROM follow_store WHERE user_id = #{userId}")
    int clearFollows(@Param("userId") Integer userId);

    @Delete("DELETE FROM follow_topic WHERE user_id = #{userId}")
    int clearTopicFollows(@Param("userId") Integer userId);

    @Select("SELECT id, item_title AS title, '收藏商品' AS `desc`, 'favorite' AS type, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS createdAt, " +
            "CASE WHEN goods_id IS NOT NULL AND goods_id <> 1 THEN goods_id " +
            "ELSE COALESCE((SELECT g.goods_id FROM goods g WHERE g.goods_name COLLATE utf8mb4_general_ci = favorite_goods.item_title COLLATE utf8mb4_general_ci ORDER BY g.goods_id DESC LIMIT 1), goods_id) END AS targetId " +
            "FROM favorite_goods WHERE user_id = #{userId} ORDER BY id DESC LIMIT 20")
    List<InteractionItem> favorites(@Param("userId") Integer userId);

    @Select("SELECT id, item_title AS title, '浏览足迹' AS `desc`, 'history' AS type, DATE_FORMAT(viewed_at, '%Y-%m-%d %H:%i') AS createdAt, " +
            "CASE WHEN goods_id IS NOT NULL AND goods_id <> 1 THEN goods_id " +
            "ELSE COALESCE((SELECT g.goods_id FROM goods g WHERE g.goods_name COLLATE utf8mb4_general_ci = browse_history.item_title COLLATE utf8mb4_general_ci ORDER BY g.goods_id DESC LIMIT 1), goods_id) END AS targetId " +
            "FROM browse_history WHERE user_id = #{userId} ORDER BY id DESC LIMIT 20")
    List<InteractionItem> browseHistory(@Param("userId") Integer userId);

    @Select("SELECT id, store_name AS title, '关注店铺' AS `desc`, 'follow' AS type, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS createdAt, store_id AS targetId " +
            "FROM follow_store WHERE user_id = #{userId} ORDER BY id DESC LIMIT 20")
    List<InteractionItem> follows(@Param("userId") Integer userId);

    @Select("SELECT id, topic_title AS title, '关注话题' AS `desc`, 'topicFollow' AS type, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS createdAt, topic_id AS targetId " +
            "FROM follow_topic WHERE user_id = #{userId} ORDER BY id DESC LIMIT 20")
    List<InteractionItem> topicFollows(@Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = #{tableName} AND COLUMN_NAME = #{columnName}")
    int countColumn(@Param("tableName") String tableName, @Param("columnName") String columnName);
}
