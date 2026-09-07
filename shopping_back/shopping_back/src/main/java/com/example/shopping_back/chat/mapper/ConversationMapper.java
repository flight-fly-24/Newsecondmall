package com.example.shopping_back.chat.mapper;

import com.example.shopping_back.chat.model.Conversation;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ConversationMapper {

    @Insert("INSERT INTO conversation (buyer_id, seller_id, goods_id, status, create_time, update_time) " +
            "VALUES (#{buyerId}, #{sellerId}, #{goodsId}, #{status}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "covId", keyColumn = "cov_id")
    int insert(Conversation conversation);

    @Select("SELECT cov_id, buyer_id, seller_id, goods_id, status, create_time, update_time FROM conversation WHERE cov_id = #{covId}")
    @Results(id = "ConversationMap", value = {
            @Result(property = "covId", column = "cov_id"),
            @Result(property = "buyerId", column = "buyer_id"),
            @Result(property = "sellerId", column = "seller_id"),
            @Result(property = "goodsId", column = "goods_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time")
    })
    Conversation selectById(Integer covId);

    @Select("SELECT * FROM conversation WHERE buyer_id = #{userId} OR seller_id = #{userId}")
    @ResultMap("ConversationMap")
    List<Conversation> selectByUserId(Integer userId);

    @Update("UPDATE conversation SET status = #{status} WHERE cov_id = #{covId}")
    int updateStatus(@Param("covId") Integer covId, @Param("status") String status);

    @Select("SELECT * FROM conversation WHERE buyer_id = #{buyerId} AND goods_id = #{goodsId} LIMIT 1")
    @ResultMap("ConversationMap")
    Conversation findByBuyerAndGoods(@Param("buyerId") Integer buyerId, @Param("goodsId") Integer goodsId);

    @Update("UPDATE conversation SET update_time = NOW() WHERE cov_id = #{covId}")
    void updateLastActiveTime(@Param("covId") Integer covId);

    @Delete("DELETE FROM conversation WHERE cov_id = #{covId}")
    int deleteById(@Param("covId") Integer covId);
}
