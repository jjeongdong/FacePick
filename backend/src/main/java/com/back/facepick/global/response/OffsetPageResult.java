package com.back.facepick.global.response;

import java.util.List;

/** 페이지 번호 화면(관리자, 공지, 문의, 차단 목록)용 오프셋 페이징 응답. */
public record OffsetPageResult<T>(List<T> content, int page, int size, long totalElements, boolean hasNext) {
    public static <T> OffsetPageResult<T> of(List<T> content, int page, int size, long totalElements) {
        return new OffsetPageResult<>(content, page, size, totalElements, (long) (page + 1) * size < totalElements);
    }
}
