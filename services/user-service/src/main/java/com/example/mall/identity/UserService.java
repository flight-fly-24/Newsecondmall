package com.example.mall.identity;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class UserService {
    private static final String UNUSABLE_PASSWORD_HASH = "!";
    private final JdbcClient jdbc;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UserView create(CreateUser request) {
        try {
            String encodedPassword = passwordEncoder.encode(request.password());
            jdbc.sql("INSERT INTO users(username, password_hash, phone, role) VALUES (:username, :passwordHash, :phone, :role)")
                    .param("username", request.username())
                    .param("passwordHash", encodedPassword)
                    .param("phone", request.phone())
                    .param("role", request.role())
                    .update();
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already exists");
        }
        return jdbc.sql("SELECT user_id, username, phone, role, credit, status FROM users WHERE username=:username")
                .param("username", request.username()).query(UserView.class).single();
    }

    public UserView find(long id) {
        Optional<UserView> user = jdbc.sql("SELECT user_id, username, phone, role, credit, status FROM users WHERE user_id=:id")
                .param("id", id).query(UserView.class).optional();
        return user.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    }

    public AddressView findAddress(long userId, long addressId) {
        return jdbc.sql("""
                SELECT address_id, user_id, receiver AS receiver_name, phone AS receiver_phone,
                       CONCAT_WS(' ', province, city, district, detail) AS detail_address, is_default
                FROM user_address WHERE user_id=:userId AND address_id=:addressId
                """).param("userId", userId).param("addressId", addressId).query(AddressView.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "address not found"));
    }

    public record CreateUser(String username, String password, String phone, String role) {}
    public record UserView(long userId, String username, String phone, String role, int credit, String status) {}
    public record AddressView(long addressId, long userId, String receiverName, String receiverPhone,
                              String detailAddress, boolean isDefault) {}
}
