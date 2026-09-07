package com.example.mall.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAuthClientTest {
    private HttpAuthClient client;
    private MockRestServiceServer server;

    @BeforeEach void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpAuthClient("http://unused.test");
        ReflectionTestUtils.setField(client, "client", builder.build());
    }

    @Test void validatesHeaderAndParsesDefaults() {
        assertThatThrownBy(() -> client.requireUser(" ")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        server.expect(requestTo("http://auth.test/api/auth/me")).andExpect(header("Authorization", "Bearer ok"))
                .andRespond(withSuccess("{\"data\":{\"userId\":7,\"username\":\"alice\",\"role\":\"seller\"}}", org.springframework.http.MediaType.APPLICATION_JSON));
        CatalogUser user = client.requireUser("Bearer ok");
        assertThat(user.userId()).isEqualTo(7); assertThat(user.credit()).isEqualTo(100);
    }

    @Test void rejectsMissingDataAndDisabledUser() {
        server.expect(requestTo("http://auth.test/api/auth/me")).andRespond(withSuccess("{\"data\":null}", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://auth.test/api/auth/me")).andRespond(withSuccess("{\"data\":{\"userId\":1,\"status\":\"blocked\"}}", org.springframework.http.MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.requireUser("a")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        assertThatThrownBy(() -> client.requireUser("b")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test void mapsRemoteStatuses() {
        server.expect(requestTo("http://auth.test/api/auth/me")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("http://auth.test/api/auth/me")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> client.requireUser("a")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        assertThatThrownBy(() -> client.requireUser("b")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
    }
}
