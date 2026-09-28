package com.back.facepick.global.response;

import java.util.List;

/** 무한스크롤·누적 목록(게시글, 채팅, 알림)용 커서 페이징 응답. nextCursor 는 정렬 키를 인코딩한 문자열 하나다. */
public record CursorPageResult<T>(List<T> content, String nextCursor, boolean hasNext) {
    public static <T> CursorPageResult<T> empty() {
        return new CursorPageResult<>(List.of(), null, false);
    }
}
