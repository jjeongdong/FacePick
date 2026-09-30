package com.back.facepick.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.back.facepick.album.domain.AlbumExpiryNoticeRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlbumExpiryNoticeScannerTest {

    @Test
    @DisplayName("지금부터 설정한 일수 뒤까지 만료되는 앨범을 대상으로 행을 만든다")
    void scansWindowOfDaysBefore() {
        // given
        AlbumExpiryNoticeRepository repository = mock(AlbumExpiryNoticeRepository.class);
        given(repository.createPendingForAlbumsExpiringBetween(any(), any())).willReturn(3);
        AlbumExpiryNoticeScanner scanner = new AlbumExpiryNoticeScanner(repository, 7);
        LocalDateTime before = LocalDateTime.now();

        // when
        scanner.scan();

        // then
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> until = ArgumentCaptor.forClass(LocalDateTime.class);
        then(repository).should().createPendingForAlbumsExpiringBetween(now.capture(), until.capture());
        assertThat(now.getValue()).isAfterOrEqualTo(before);
        assertThat(Duration.between(now.getValue(), until.getValue())).isEqualTo(Duration.ofDays(7));
    }
}
