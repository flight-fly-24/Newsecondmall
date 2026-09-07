package com.example.shopping_back.shop.mapper;

import com.example.shopping_back.shop.model.ProductRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopProductMapper {
    String BASE_SELECT = """
            SELECT g.goods_id AS goodsId,
                   g.seller_id AS sellerId,
                   s.store_id AS storeId,
                   COALESCE(s.store_name, u.username, '个人卖家') AS sellerName,
                   COALESCE(u.credit, 96) AS sellerCredit,
                   g.goods_name AS goodsName,
                   g.category,
                   g.goods_desc AS goodsDesc,
                   g.goods_condition AS goodsCondition,
                   g.story,
                   g.price,
                   g.floor_price AS floorPrice,
                   g.scene,
                   g.address,
                   g.image,
                   g.status,
                   g.reject_reason AS rejectReason,
                   g.reviewed_at AS reviewedAt,
                   g.create_time AS createTime
            FROM goods g
            LEFT JOIN users u ON g.seller_id = u.user_id
            LEFT JOIN store s ON s.store_id = (
                SELECT st.store_id
                FROM store st
                WHERE st.seller_id = g.seller_id AND st.status = 'normal'
                ORDER BY st.store_id
                LIMIT 1
            )
            """;

    @Select("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods' AND COLUMN_NAME = #{column}")
    int countGoodsColumn(@Param("column") String column);

    @Select("""
            SELECT CHARACTER_MAXIMUM_LENGTH
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'goods' AND COLUMN_NAME = 'status'
            """)
    Integer statusColumnLength();

    @Update("ALTER TABLE goods MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'approved' COMMENT 'pending/approved/rejected/offline'")
    void widenStatusColumn();

    @Update("ALTER TABLE goods ADD COLUMN reject_reason VARCHAR(255) DEFAULT NULL COMMENT '商品审核拒绝理由'")
    void addRejectReasonColumn();

    @Update("ALTER TABLE goods ADD COLUMN reviewed_at DATETIME DEFAULT NULL COMMENT '商品审核时间'")
    void addReviewedAtColumn();

    @Update("UPDATE goods SET status = 'approved' WHERE status = '0'")
    int migrateApprovedStatus();

    @Update("UPDATE goods SET status = 'pending' WHERE status = '2'")
    int migratePendingStatus();

    @Update("UPDATE goods SET status = '2' WHERE status = 'pe'")
    int migrateTruncatedPendingStatus();

    @Insert("""
            INSERT INTO goods (seller_id, goods_name, category, goods_desc, goods_condition, story,
                               price, floor_price, scene, address, image, status)
            VALUES (#{sellerId}, #{goodsName}, #{category}, #{goodsDesc}, #{goodsCondition}, #{story},
                    #{price}, #{floorPrice}, #{scene}, #{address}, #{image}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "goodsId", keyColumn = "goods_id")
    int insert(ProductRecord product);

    @Update("""
            UPDATE goods
            SET goods_name = #{goodsName},
                category = #{category},
                goods_desc = #{goodsDesc},
                goods_condition = #{goodsCondition},
                story = #{story},
                price = #{price},
                floor_price = #{floorPrice},
                address = #{address},
                image = #{image}
            WHERE goods_id = #{goodsId}
            """)
    int update(ProductRecord product);

    @Select(BASE_SELECT + """
            WHERE g.status IN ('approved', '0')
              AND (#{scene} IS NULL OR #{scene} = '' OR #{scene} = 'all' OR g.scene = #{scene})
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR g.goods_name LIKE CONCAT('%', #{keyword}, '%')
                   OR g.goods_desc LIKE CONCAT('%', #{keyword}, '%')
                   OR g.category LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY g.create_time DESC
            """)
    List<ProductRecord> selectApproved(@Param("scene") String scene, @Param("keyword") String keyword);

    @Select(BASE_SELECT + " WHERE g.goods_id = #{id}")
    ProductRecord selectById(@Param("id") Integer id);

    @Select(BASE_SELECT + """
            WHERE g.seller_id = #{sellerId}
            ORDER BY g.create_time DESC
            """)
    List<ProductRecord> selectBySeller(@Param("sellerId") Integer sellerId);

    @Select(BASE_SELECT + """
            WHERE g.status IN ('pending', '2', 'pe')
            ORDER BY g.create_time ASC
            """)
    List<ProductRecord> selectPending();

    @Update("""
            UPDATE goods
            SET status = #{status}, reject_reason = #{rejectReason}, reviewed_at = NOW()
            WHERE goods_id = #{id}
            """)
    int updateAuditStatus(@Param("id") Integer id, @Param("status") String status, @Param("rejectReason") String rejectReason);
}
