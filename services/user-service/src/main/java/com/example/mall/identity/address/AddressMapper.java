package com.example.mall.identity.address;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AddressMapper {
    @Update("""
            CREATE TABLE IF NOT EXISTS user_address (
                address_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                receiver VARCHAR(50) NOT NULL,
                phone VARCHAR(20) NOT NULL,
                province VARCHAR(50) DEFAULT NULL,
                city VARCHAR(50) DEFAULT NULL,
                district VARCHAR(50) DEFAULT NULL,
                detail VARCHAR(255) NOT NULL,
                is_default TINYINT(1) NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_address_user (user_id)
            ) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4
            """)
    void createTable();

    @Select("""
            SELECT address_id AS addressId,
                   user_id AS userId,
                   receiver,
                   phone,
                   province,
                   city,
                   district,
                   detail,
                   is_default AS isDefault
            FROM user_address
            WHERE user_id = #{userId}
            ORDER BY is_default DESC, address_id DESC
            """)
    List<AddressRecord> selectByUser(@Param("userId") Integer userId);

    @Select("""
            SELECT address_id AS addressId,
                   user_id AS userId,
                   receiver,
                   phone,
                   province,
                   city,
                   district,
                   detail,
                   is_default AS isDefault
            FROM user_address
            WHERE address_id = #{addressId} AND user_id = #{userId}
            """)
    AddressRecord selectById(@Param("addressId") Integer addressId, @Param("userId") Integer userId);

    @Insert("""
            INSERT INTO user_address(user_id, receiver, phone, province, city, district, detail, is_default)
            VALUES(#{userId}, #{receiver}, #{phone}, #{province}, #{city}, #{district}, #{detail}, #{isDefault})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "addressId", keyColumn = "address_id")
    int insert(AddressRecord record);

    @Update("""
            UPDATE user_address
            SET receiver = #{receiver},
                phone = #{phone},
                province = #{province},
                city = #{city},
                district = #{district},
                detail = #{detail}
            WHERE address_id = #{addressId} AND user_id = #{userId}
            """)
    int update(AddressRecord record);

    @Delete("DELETE FROM user_address WHERE address_id = #{addressId} AND user_id = #{userId}")
    int delete(@Param("addressId") Integer addressId, @Param("userId") Integer userId);

    @Update("UPDATE user_address SET is_default = 0 WHERE user_id = #{userId}")
    int clearDefault(@Param("userId") Integer userId);

    @Update("UPDATE user_address SET is_default = 1 WHERE address_id = #{addressId} AND user_id = #{userId}")
    int setDefault(@Param("addressId") Integer addressId, @Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM user_address WHERE user_id = #{userId}")
    int countByUser(@Param("userId") Integer userId);
}
