package com.example.mall.interaction;

import com.example.mall.common.AuthClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Configuration
public class InteractionClientConfiguration {

    @Value("${app.circuit-breaker.sliding-window-size:10}")
    private int slidingWindowSize;
    @Value("${app.circuit-breaker.failure-rate-threshold:50}")
    private float failureRateThreshold;
    @Value("${app.circuit-breaker.wait-duration-in-open-state-ms:30000}")
    private long waitDurationInOpenStateMs;
    @Value("${app.circuit-breaker.permitted-calls-in-half-open-state:3}")
    private int permittedCallsInHalfOpenState;

    @Bean
    @Qualifier("authRestClient")
    RestClient authRestClient(@Value("${app.auth.base-url:http://127.0.0.1:8080}") String baseUrl) {
        return restClient(baseUrl, 500, 1000);
    }

    @Bean
    AuthClient authClient(@Qualifier("authRestClient") RestClient authRestClient) {
        return new AuthClient(authRestClient);
    }

    @Bean
    @Qualifier("catalogRestClient")
    RestClient catalogRestClient(@Value("${app.catalog.base-url:http://127.0.0.1:8082}") String baseUrl) {
        return restClient(baseUrl, 500, 1000);
    }

    @Bean
    CircuitBreaker catalogCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(slidingWindowSize)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenStateMs))
                .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpenState)
                // 只有 5xx 响应或连接/读写超时计为失败；4xx 等业务错误不影响熔断统计
                .recordException(InteractionClientConfiguration::isRemoteFailure)
                .build();
        return CircuitBreaker.of("catalog", config);
    }

    static boolean isRemoteFailure(Throwable throwable) {
        if (throwable instanceof RestClientResponseException response) {
            return response.getStatusCode().is5xxServerError();
        }
        return throwable instanceof ResourceAccessException;
    }

    private static RestClient restClient(String baseUrl, int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        String url = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return RestClient.builder().baseUrl(url).requestFactory(requestFactory).build();
    }
}
