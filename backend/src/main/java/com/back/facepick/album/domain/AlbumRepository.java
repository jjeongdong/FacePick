package com.back.facepick.album.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AlbumRepository {
    Album save(Album album);

    Album getById(Long albumId);

    // 코드에 맞는 앨범이 없으면 AlbumInviteNotFoundException.
    Album getByInviteCode(String inviteCode);

    /** 없는 ID 는 결과에서 빠진다. */
    List<Album> findAllByIds(Collection<Long> albumIds);

    /** 만료 시각이 now 이전(같은 시각 포함)인 앨범 ID 를 만료 시각·ID 오름차순으로 최대 limit 개. */
    List<Long> findExpiredIds(LocalDateTime now, int limit);

    /** 만료된 앨범 행을 지운다. 멤버·만료 알림 행은 FK CASCADE 로 DB 가 지운다. 없거나 만료 전이면 0. */
    int deleteExpired(Long albumId, LocalDateTime now);
}
