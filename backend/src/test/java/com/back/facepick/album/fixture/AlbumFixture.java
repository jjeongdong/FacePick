package com.back.facepick.album.fixture;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumRole;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class AlbumFixture {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    private AlbumFixture() {}

    public static Album album(Long albumId, Long ownerId) {
        Album album = Album.create(ownerId, "제주 여행", NOW);
        // 저장 없이 쓰는 단위 테스트용이라 id·생성 시각을 리플렉션으로 채운다.
        ReflectionTestUtils.setField(album, "id", albumId);
        ReflectionTestUtils.setField(album, "createdAt", NOW);
        return album;
    }

    public static AlbumMember member(Album album, Long userId, AlbumRole role) {
        AlbumMember member = AlbumMember.create(album, userId, role);
        ReflectionTestUtils.setField(member, "createdAt", NOW);
        return member;
    }
}
