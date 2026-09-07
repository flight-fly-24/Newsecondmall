package com.example.mall.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user_service;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
class UserServiceContextIT {
    @Autowired UserService service;

    @Test
    void createsAndReadsUserFromIdentityDatabase() {
        UserService.UserView created = service.create(new UserService.CreateUser("micro-user", "test123", "13800000000", "buyer"));
        assertThat(service.find(created.userId()).username()).isEqualTo("micro-user");
    }

    @Test
    void rejectsUnknownUser() {
        assertThatThrownBy(() -> service.find(9999)).isInstanceOf(ResponseStatusException.class);
    }
}
