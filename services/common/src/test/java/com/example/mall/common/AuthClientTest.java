package com.example.mall.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthClientTest {
    private MockRestServiceServer server;
    private AuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test")
                .messageConverters(converters -> converters.add(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json().build())));
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AuthClient(builder.build());
    }

    @Test
    void optionalUserReturnsNullForMissingOrMalformedAuthorization() {
        assertThat(client.optionalUser(null)).isNull();
        assertThat(client.optionalUser("Basic abc")).isNull();
        assertThat(client.optionalUser("Bearer   ")).isNull();
    }

    @Test
    void requireProfileForwardsBearerTokenAndParsesProfile() {
        server.expect(requestTo("http://auth.test/api/auth/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("{\"code\":0,\"message\":\"ok\",\"data\":{\"userId\":7,\"username\":\"alice\",\"role\":\"buyer\",\"status\":\"normal\"}}", org.springframework.http.MediaType.APPLICATION_JSON));
        assertThat(client.requireProfile(" bearer token-1 ").username()).isEqualTo("alice");
        server.verify();
    }

    @Test
    void requireUserRejectsMissingAndInvalidProfiles() {
        assertThatThrownBy(() -> client.requireUser(null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        server.expect(requestTo("http://auth.test/api/auth/me"))
                .andRespond(withSuccess("{\"code\":0,\"message\":\"ok\",\"data\":null}", org.springframework.http.MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.requireUser("Bearer expired"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
    }

    @Test
    void optionalUserConvertsUnauthorizedToNull() {
        server.expect(requestTo("http://auth.test/api/auth/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThat(client.optionalUser("Bearer expired")).isNull();
    }

    @Test
    void upstreamFailureBecomesServiceUnavailable() {
        server.expect(requestTo("http://auth.test/api/auth/me"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> client.requireProfile("Bearer token"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
    }
}
