package com.back.facepick.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CursorPageResultTest {

    @Test
    @DisplayName("JSON 키는 content, nextCursor, hasNext 다")
    void serializesKeys() throws Exception {
        // given
        CursorPageResult<String> result = new CursorPageResult<>(List.of("a"), "abc", true);

        // when
        String json = new ObjectMapper().writeValueAsString(result);

        // then
        assertThat(json).isEqualTo("{\"content\":[\"a\"],\"nextCursor\":\"abc\",\"hasNext\":true}");
    }

    @Test
    @DisplayName("빈 결과는 다음 커서가 없다")
    void empty() {
        // when
        CursorPageResult<String> result = CursorPageResult.empty();

        // then
        assertThat(result.content()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
    }
}
