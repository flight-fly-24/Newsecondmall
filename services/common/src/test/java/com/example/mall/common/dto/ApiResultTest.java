package com.example.mall.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiResultTest {
    @Test
    void okPreservesGenericData() {
        ApiResult<List<String>> result = ApiResult.ok(List.of("a", "b"));
        assertThat(result.getCode()).isZero();
        assertThat(result.getMessage()).isEqualTo("ok");
        assertThat(result.getData()).containsExactly("a", "b");
    }

    @Test
    void failPreservesCodeAndMessageAndHasNoData() {
        ApiResult<Object> result = ApiResult.fail(409, "conflict");
        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getMessage()).isEqualTo("conflict");
        assertThat(result.getData()).isNull();
    }

    @Test
    void constructorAcceptsBoundaryValues() {
        ApiResult<String> result = new ApiResult<>(-1, "", null);
        assertThat(result.getCode()).isEqualTo(-1);
        assertThat(result.getMessage()).isEmpty();
        assertThat(result.getData()).isNull();
    }
}
