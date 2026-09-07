package com.example.shopping_back.shop.mapper;

import com.example.shopping_back.shop.model.OrderRecord;
import com.example.shopping_back.shop.model.ProductReviewRecord;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopOrderMapper {
    @Update("""
            CREATE TABLE IF NOT EXISTS orders (
                order_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                buyer_id INT NOT NULL,
                seller_id INT NOT NULL,
                goods_id INT DEFAULT NULL,
                status VARCHAR(30) NOT NULL,
                amount DECIMAL(10,2) NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4
            """)
    void createOrdersTable();

    @Update("""
            CREATE TABLE IF NOT EXISTS product_review (
                review_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                order_id INT NOT NULL,
                goods_id INT NOT NULL,
                buyer_id INT NOT NULL,
                seller_id INT NOT NULL,
                product_score INT NOT NULL,
                seller_score INT NOT NULL,
                content VARCHAR(1000) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uk_review_order (order_id),
                INDEX idx_review_goods (goods_id, created_at),
                INDEX idx_review_seller (seller_id)
            ) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4
            """)
    void createReviewTable();

    @Insert("""
            INSERT INTO orders(buyer_id, seller_id, goods_id, status, amount)
            VALUES(#{buyerId}, #{sellerId}, #{goodsId}, #{status}, #{amount})
            """)
    int insertOrder(
            @Param("buyerId") Integer buyerId,
            @Param("sellerId") Integer sellerId,
            @Param("goodsId") Integer goodsId,
            @Param("status") String status,
            @Param("amount") BigDecimal amount);

    @Select("""
            SELECT o.order_id AS orderId,
                   o.buyer_id AS buyerId,
                   o.seller_id AS sellerId,
                   o.goods_id AS goodsId,
                   o.status,
                   o.amount,
                   o.created_at AS createdAt,
                   g.goods_name AS goodsName,
                   g.image AS goodsImage,
                   g.scene,
                   g.category,
                   COALESCE(s.store_name, u.username, '个人卖家') AS sellerName,
                   r.product_score AS productScore,
                   r.seller_score AS sellerScore,
                   r.content AS reviewContent,
                   r.created_at AS reviewedAt
            FROM orders o
            LEFT JOIN goods g ON g.goods_id = o.goods_id
            LEFT JOIN users u ON u.user_id = o.seller_id
            LEFT JOIN store s ON s.store_id = (
                SELECT st.store_id FROM store st
                WHERE st.seller_id = o.seller_id AND st.status = 'normal'
                ORDER BY st.store_id LIMIT 1
            )
            LEFT JOIN product_review r ON r.order_id = o.order_id
            WHERE o.order_id = #{orderId}
            """)
    OrderRecord selectOrder(@Param("orderId") Integer orderId);

    @Select("""
            SELECT r.review_id AS reviewId,
                   r.order_id AS orderId,
                   r.goods_id AS goodsId,
                   r.buyer_id AS buyerId,
                   r.seller_id AS sellerId,
                   r.product_score AS productScore,
                   r.seller_score AS sellerScore,
                   r.content,
                   r.created_at AS createdAt,
                   COALESCE(u.username, '买家') AS buyerName
            FROM product_review r
            LEFT JOIN users u ON u.user_id = r.buyer_id
            WHERE r.goods_id = #{goodsId}
            ORDER BY r.created_at DESC, r.review_id DESC
            LIMIT 20
            """)
    List<ProductReviewRecord> selectProductReviews(@Param("goodsId") Integer goodsId);

    @Select("SELECT COUNT(*) FROM product_review WHERE order_id = #{orderId}")
    int reviewCountByOrder(@Param("orderId") Integer orderId);

    @Insert("""
            INSERT INTO product_review(order_id, goods_id, buyer_id, seller_id, product_score, seller_score, content)
            VALUES(#{orderId}, #{goodsId}, #{buyerId}, #{sellerId}, #{productScore}, #{sellerScore}, #{content})
            """)
    int insertReview(
            @Param("orderId") Integer orderId,
            @Param("goodsId") Integer goodsId,
            @Param("buyerId") Integer buyerId,
            @Param("sellerId") Integer sellerId,
            @Param("productScore") Integer productScore,
            @Param("sellerScore") Integer sellerScore,
            @Param("content") String content);

    @Update("""
            UPDATE users
            SET credit = (
                SELECT ROUND(AVG(seller_score) * 20)
                FROM product_review
                WHERE seller_id = #{sellerId}
            )
            WHERE user_id = #{sellerId}
            """)
    int refreshSellerCredit(@Param("sellerId") Integer sellerId);

    @Update("""
            UPDATE store
            SET score = (
                    SELECT ROUND(AVG(seller_score), 1)
                    FROM product_review
                    WHERE seller_id = #{sellerId}
                ),
                credit_score = (
                    SELECT ROUND(AVG(seller_score) * 20)
                    FROM product_review
                    WHERE seller_id = #{sellerId}
                )
            WHERE seller_id = #{sellerId}
            """)
    int refreshStoreCredit(@Param("sellerId") Integer sellerId);

    @Select("""
            SELECT o.order_id AS orderId,
                   o.buyer_id AS buyerId,
                   o.seller_id AS sellerId,
                   o.goods_id AS goodsId,
                   o.status,
                   o.amount,
                   o.created_at AS createdAt,
                   g.goods_name AS goodsName,
                   g.image AS goodsImage,
                   g.scene,
                   g.category,
                   COALESCE(s.store_name, u.username, '个人卖家') AS sellerName,
                   r.product_score AS productScore,
                   r.seller_score AS sellerScore,
                   r.content AS reviewContent,
                   r.created_at AS reviewedAt
            FROM orders o
            LEFT JOIN goods g ON g.goods_id = o.goods_id
            LEFT JOIN users u ON u.user_id = o.seller_id
            LEFT JOIN store s ON s.store_id = (
                SELECT st.store_id FROM store st
                WHERE st.seller_id = o.seller_id AND st.status = 'normal'
                ORDER BY st.store_id LIMIT 1
            )
            LEFT JOIN product_review r ON r.order_id = o.order_id
            WHERE o.buyer_id = #{buyerId}
              AND (#{status} IS NULL OR #{status} = '' OR o.status = #{status})
            ORDER BY o.created_at DESC, o.order_id DESC
            """)
    List<OrderRecord> selectBuyerOrdersFiltered(@Param("buyerId") Integer buyerId, @Param("status") String status);

    @Update("UPDATE orders SET status = #{status} WHERE order_id = #{orderId}")
    int updateOrderStatus(@Param("orderId") Integer orderId, @Param("status") String status);
}
