package com.back.facepick.album.domain;

import java.util.List;

public interface AlbumMemberRepository {
    AlbumMember save(AlbumMember albumMember);

    // 참여자가 아니면 AlbumNotMemberException.
    AlbumMember getByAlbumIdAndUserId(Long albumId, Long userId);

    /** 최근 참여순 (같으면 id 내림차순). 앨범을 함께 불러온다. */
    List<AlbumMember> findAllByUserIdWithAlbum(Long userId);
}
