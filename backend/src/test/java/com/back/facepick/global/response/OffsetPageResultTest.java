package com.back.facepick.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OffsetPageResultTest {

    @Test
    @DisplayName("뒤에 남은 항목이 있으면 hasNext 가 true")
    void hasNextWhenMoreRemain() {
        assertThat(OffsetPageResult.of(List.of(1, 2), 0, 2, 3).hasNext()).isTrue();
    }

    @Test
    @DisplayName("마지막 페이지가 딱 맞게 끝나면 hasNext 가 false")
    void noNextOnExactLastPage() {
        assertThat(OffsetPageResult.of(List.of(3, 4), 1, 2, 4).hasNext()).isFalse();
    }

    @Test
    @DisplayName("결과가 없으면 hasNext 가 false")
    void noNextWhenEmpty() {
        assertThat(OffsetPageResult.of(List.of(), 0, 20, 0).hasNext()).isFalse();
    }
}
