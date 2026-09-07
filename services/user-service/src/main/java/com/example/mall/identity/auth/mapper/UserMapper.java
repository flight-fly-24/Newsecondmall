package com.example.mall.identity.auth.mapper;

import com.example.mall.identity.auth.model.StoredUser;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {
    @Insert("INSERT INTO users(username, password_hash, phone, role) " +
            "VALUES(#{username}, #{passwordHash}, #{phone}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "userId", keyColumn = "user_id")
    int insertUser(StoredUser user);

    @Select("SELECT user_id AS userId, username, password_hash AS passwordHash, phone, credit, role, " +
            "COALESCE(status, 'normal') AS status, avatar_url AS avatarUrl " +
            "FROM users WHERE username = #{username} ORDER BY user_id LIMIT 1")
    StoredUser findByUsername(String username);

    @Select("SELECT user_id AS userId, username, password_hash AS passwordHash, phone, credit, role, " +
            "COALESCE(status, 'normal') AS status, avatar_url AS avatarUrl " +
            "FROM users WHERE phone = #{phone} ORDER BY user_id LIMIT 1")
    StoredUser findByPhone(@Param("phone") String phone);

    @Select("SELECT user_id AS userId, username, phone, credit, role, COALESCE(status, 'normal') AS status, avatar_url AS avatarUrl " +
            "FROM users WHERE username LIKE CONCAT('%', #{keyword}, '%') ESCAPE '\\\\'")
    List<StoredUser> searchUsersByKeyword(@Param("keyword") String keyword);

    @Select("SELECT * FROM users WHERE user_id = #{userId}")
    StoredUser findById(@Param("userId") Integer userId);

    @Update("UPDATE users SET role = #{role} WHERE username = #{username}")
    int updateRoleByUsername(@Param("username") String username, @Param("role") String role);

    @Update("UPDATE users SET avatar_url = #{avatarUrl} WHERE user_id = #{userId}")
    int updateAvatarUrl(@Param("userId") Integer userId, @Param("avatarUrl") String avatarUrl);

    @Select("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = #{columnName}")
    int countUserColumn(@Param("columnName") String columnName);
}
