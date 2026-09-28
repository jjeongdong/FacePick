package com.back.facepick.album.application;

import com.back.facepick.album.domain.AlbumMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlbumQueryApi {
    private final AlbumMemberRepository albumMemberRepository;

    /**
     * 사용자가 앨범 참여자인지 확인한다. 앨범 만료 여부는 보지 않는다.
     *
     * @param albumId 앨범 ID
     * @param userId 사용자 ID
     * @return 참여자면 true. 없는 앨범이면 false
     */
    @Transactional(readOnly = true)
    public boolean isMember(Long albumId, Long userId) {
        return albumMemberRepository.existsByAlbumIdAndUserId(albumId, userId);
    }
}
