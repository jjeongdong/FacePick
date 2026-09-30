package com.back.facepick.album.domain;

import java.util.Collection;
import java.util.List;

public interface AlbumRepository {
    Album save(Album album);

    Album getById(Long albumId);

    // 코드에 맞는 앨범이 없으면 AlbumInviteNotFoundException.
    Album getByInviteCode(String inviteCode);

    /** 없는 ID 는 결과에서 빠진다. */
    List<Album> findAllByIds(Collection<Long> albumIds);
}
