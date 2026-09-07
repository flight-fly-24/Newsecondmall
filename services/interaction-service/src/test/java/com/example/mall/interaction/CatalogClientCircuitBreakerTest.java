package com.example.mall.interaction;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class CatalogClientCircuitBreakerTest {
    /** 缩小窗口便于测试：2 次调用内失败率 ≥50% 即熔断，打开 60 秒（测试期间不自动恢复）。 */
    private static CircuitBreaker testBreaker(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordException(InteractionClientConfiguration::isRemoteFailure)
                .build();
        return CircuitBreaker.of(name, config);
    }

    @Test
    void catalogCircuitOpensAfterConsecutiveFailuresAndDegradesToNull() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://catalog.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CircuitBreaker breaker = testBreaker("catalog-open");
        CatalogClient client = new CatalogClient(builder.build(), breaker);
        server.expect(requestTo("http://catalog.test/api/products/1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo("http://catalog.test/api/products/1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // 商品服务连续 5xx：按原有降级语义返回 null（隐藏商品卡片）
        assertThat(client.product(1)).isNull();
        assertThat(client.product(1)).isNull();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 熔断打开后快速失败：不再发起远程调用，直接降级返回 null
        assertThat(client.product(1)).isNull();
        assertThat(client.store(1)).isNull();
        server.verify();
    }
}
