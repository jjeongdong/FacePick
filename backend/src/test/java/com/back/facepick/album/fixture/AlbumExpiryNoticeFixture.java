package com.back.facepick.album.fixture;

import com.back.facepick.album.domain.AlbumExpiryNotice;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class AlbumExpiryNoticeFixture {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    private AlbumExpiryNoticeFixture() {}

    public static AlbumExpiryNotice pending(Long noticeId, Long albumId, Long userId) {
        AlbumExpiryNotice notice = AlbumExpiryNotice.create(albumId, userId, NOW);
        // 저장 없이 쓰는 단위 테스트용이라 id 를 리플렉션으로 채운다.
        ReflectionTestUtils.setField(notice, "id", noticeId);
        return notice;
    }
}
