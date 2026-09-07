package com.example.mall.order;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Configuration
public class CatalogClientConfiguration {
    @Value("${app.circuit-breaker.sliding-window-size:10}")
    private int slidingWindowSize;
    @Value("${app.circuit-breaker.failure-rate-threshold:50}")
    private float failureRateThreshold;
    @Value("${app.circuit-breaker.wait-duration-in-open-state-ms:30000}")
    private long waitDurationInOpenStateMs;
    @Value("${app.circuit-breaker.permitted-calls-in-half-open-state:3}")
    private int permittedCallsInHalfOpenState;

    @Bean
    RestClient catalogRestClient(
            @Value("${app.catalog.base-url}") String baseUrl,
            @Value("${app.catalog.connect-timeout-ms:500}") int connectTimeout,
            @Value("${app.catalog.read-timeout-ms:1000}") int readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Bean
    RestClient userRestClient(@Value("${app.user.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(500);
        requestFactory.setReadTimeout(1000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Bean
    RestClient authRestClient(@Value("${app.auth.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(500);
        requestFactory.setReadTimeout(2000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Bean
    CircuitBreaker catalogCircuitBreaker() {
        return circuitBreaker("catalog");
    }

    @Bean
    CircuitBreaker userCircuitBreaker() {
        return circuitBreaker("user");
    }

    private CircuitBreaker circuitBreaker(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(slidingWindowSize)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenStateMs))
                .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpenState)
                // 只有 5xx 响应或连接/读写超时计为失败；404 等业务错误不影响熔断统计
                .recordException(CatalogClientConfiguration::isRemoteFailure)
                .build();
        return CircuitBreaker.of(name, config);
    }

    static boolean isRemoteFailure(Throwable throwable) {
        if (throwable instanceof RestClientResponseException response) {
            return response.getStatusCode().is5xxServerError();
        }
        return throwable instanceof ResourceAccessException;
    }
}
