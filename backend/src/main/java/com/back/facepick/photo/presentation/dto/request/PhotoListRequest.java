package com.back.facepick.photo.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 쿼리 파라미터 ?cursor=&size= 를 받는다. 조회라 Command 로 바꾸지 않고 값을 그대로 Service 에 넘긴다.
public record PhotoListRequest(
        String cursor,
        @Min(value = 1, message = "size 는 1 이상 100 이하여야 합니다.") @Max(value = 100, message = "size 는 1 이상 100 이하여야 합니다.")
                Integer size) {
    private static final int DEFAULT_SIZE = 20;

    public PhotoListRequest {
        if (size == null) {
            size = DEFAULT_SIZE;
        }
    }
}
