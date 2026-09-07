package com.example.mall.order;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpClientsTest {
    @Test void userClientCoversLoginAndUserAddressChecks() {
        RestClient.Builder auth = RestClient.builder().baseUrl("http://auth.test");
        RestClient.Builder users = RestClient.builder().baseUrl("http://users.test");
        MockRestServiceServer authServer = MockRestServiceServer.bindTo(auth).build();
        MockRestServiceServer userServer = MockRestServiceServer.bindTo(users).build();
        HttpUserClient client = new HttpUserClient(auth.build(), users.build());
        assertThatThrownBy(() -> client.requireLogin(null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        authServer.expect(requestTo("http://auth.test/api/auth/me")).andRespond(withSuccess("{\"data\":{\"userId\":3,\"status\":\"normal\"}}", org.springframework.http.MediaType.APPLICATION_JSON));
        authServer.expect(requestTo("http://auth.test/api/auth/me")).andRespond(withSuccess("{\"data\":null}", org.springframework.http.MediaType.APPLICATION_JSON));
        assertThat(client.requireLogin("Bearer ok").userId()).isEqualTo(3);
        assertThatThrownBy(() -> client.requireLogin("bad")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        userServer.expect(requestTo("http://users.test/internal/users/3")).andRespond(withSuccess("{\"userId\":3,\"status\":\"normal\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        userServer.expect(requestTo("http://users.test/internal/users/3/addresses/8")).andRespond(withSuccess());
        client.requireActiveUserAndAddress(3, 8);
    }

    @Test void userClientMapsRemoteFailures() {
        RestClient.Builder auth = RestClient.builder().baseUrl("http://auth.test");
        RestClient.Builder users = RestClient.builder().baseUrl("http://users.test");
        MockRestServiceServer authServer = MockRestServiceServer.bindTo(auth).build();
        MockRestServiceServer userServer = MockRestServiceServer.bindTo(users).build();
        HttpUserClient client = new HttpUserClient(auth.build(), users.build());
        authServer.expect(requestTo("http://auth.test/api/auth/me")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.requireLogin("bad")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        userServer.expect(requestTo("http://users.test/internal/users/9")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.requireActiveUser(9)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("422");
    }

    @Test void catalogClientGetsProductAndMapsFailures() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://catalog.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCatalogClient client = new HttpCatalogClient(builder.build());
        server.expect(requestTo("http://catalog.test/internal/products/4/snapshot"))
                .andRespond(withSuccess("{\"productId\":4,\"sellerId\":2,\"name\":\"phone\",\"price\":10,\"status\":\"on_sale\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://catalog.test/internal/products/4/sold")).andExpect(header("X-Order-Number", "O1")).andRespond(withSuccess());
        server.expect(requestTo("http://catalog.test/internal/products/99/snapshot")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(client.getProduct(4).price()).isEqualByComparingTo(BigDecimal.TEN);
        client.markSold(4, "O1");
        assertThatThrownBy(() -> client.getProduct(99)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("422");
    }
}
