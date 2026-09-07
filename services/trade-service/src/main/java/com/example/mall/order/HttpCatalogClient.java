package com.example.mall.order;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpCatalogClient implements CatalogClient {
    private final RestClient client;
    private final CircuitBreaker circuitBreaker;

    public HttpCatalogClient(RestClient catalogRestClient) {
        this(catalogRestClient, CircuitBreaker.ofDefaults("catalog"));
    }

    @Autowired
    public HttpCatalogClient(RestClient catalogRestClient,
                             @Qualifier("catalogCircuitBreaker") CircuitBreaker catalogCircuitBreaker) {
        this.client = catalogRestClient;
        this.circuitBreaker = catalogCircuitBreaker;
    }

    @Override
    public ProductSnapshot getProduct(long productId) {
        try {
            ProductSnapshot product = circuitBreaker.executeSupplier(() -> client.get()
                    .uri("/internal/products/{id}/snapshot", productId)
                    .retrieve().body(ProductSnapshot.class));
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "catalog returned an empty response");
            }
            return product;
        } catch (CallNotPermittedException exception) {
            // 熔断打开：快速失败，不再发起远程调用
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "product does not exist");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "catalog request failed", exception);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "catalog is unavailable", exception);
        }
    }

    @Override
    public void markSold(long productId, String orderNumber) {
        try {
            circuitBreaker.executeRunnable(() -> client.put().uri("/internal/products/{id}/sold", productId)
                    .header("X-Order-Number", orderNumber).retrieve().toBodilessEntity());
        } catch (CallNotPermittedException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试");
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "catalog sale update failed", exception);
        }
    }
}
