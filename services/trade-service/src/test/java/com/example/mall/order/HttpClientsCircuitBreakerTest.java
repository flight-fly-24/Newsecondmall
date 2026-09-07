package com.example.mall.order;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpClientsCircuitBreakerTest {
    /** 缩小窗口便于测试：2 次调用内失败率 ≥50% 即熔断，打开 60 秒（测试期间不自动恢复）。 */
    private static CircuitBreaker testBreaker(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordException(CatalogClientConfiguration::isRemoteFailure)
                .build();
        return CircuitBreaker.of(name, config);
    }

    @Test
    void catalogCircuitOpensAfterConsecutiveFailuresAndFailsFast() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://catalog.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CircuitBreaker breaker = testBreaker("catalog-open");
        HttpCatalogClient client = new HttpCatalogClient(builder.build(), breaker);
        server.expect(requestTo("http://catalog.test/internal/products/4/snapshot"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo("http://catalog.test/internal/products/4/snapshot"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getProduct(4))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
        assertThatThrownBy(() -> client.getProduct(4))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 熔断打开后快速失败：不再发起远程调用，直接返回 503 中文提示
        assertThatThrownBy(() -> client.getProduct(4))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503")
                .hasMessageContaining("商品服务暂不可用，请稍后重试");
        server.verify();
    }

    @Test
    void catalogBusinessErrorsDoNotTripTheCircuit() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://catalog.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CircuitBreaker breaker = testBreaker("catalog-business");
        HttpCatalogClient client = new HttpCatalogClient(builder.build(), breaker);
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo("http://catalog.test/internal/products/99/snapshot"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));
        }

        // 404 属于业务错误，不计入熔断统计；连续 404 后熔断器仍保持关闭
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> client.getProduct(99))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("422");
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }

    @Test
    void userCircuitOpensAfterConsecutiveFailuresAndFailsFast() {
        RestClient.Builder auth = RestClient.builder().baseUrl("http://auth.test");
        RestClient.Builder users = RestClient.builder().baseUrl("http://users.test");
        MockRestServiceServer userServer = MockRestServiceServer.bindTo(users).build();
        CircuitBreaker breaker = testBreaker("user-open");
        HttpUserClient client = new HttpUserClient(auth.build(), users.build(), breaker);
        userServer.expect(requestTo("http://users.test/internal/users/9"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        userServer.expect(requestTo("http://users.test/internal/users/9"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.requireActiveUser(9))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
        assertThatThrownBy(() -> client.requireActiveUser(9))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> client.requireActiveUser(9))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503")
                .hasMessageContaining("用户服务暂不可用，请稍后重试");
        userServer.verify();
    }
}
