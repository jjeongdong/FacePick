package com.back.facepick.album.application;

import com.back.facepick.album.application.dto.api.AlbumInfo;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlbumQueryApi {
    private final AlbumRepository albumRepository;
    private final AlbumMemberRepository albumMemberRepository;

    /**
     * 앨범의 공개 정보를 조회한다.
     *
     * @param albumId 앨범 ID
     * @return 앨범 ID·앨범장 ID·만료 시각
     * @throws com.back.facepick.album.domain.exception.AlbumNotFoundException 없는 앨범이면 (404)
     */
    @Transactional(readOnly = true)
    public AlbumInfo getInfo(Long albumId) {
        return AlbumInfo.from(albumRepository.getById(albumId));
    }

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

    /**
     * 만료된 앨범 ID 를 오래된 순으로 조회한다. 자동 삭제 대상 찾기용.
     *
     * @param now 기준 시각. 만료 시각이 이 시각 이전(같은 시각 포함)이면 만료다
     * @param limit 최대 개수
     * @return 만료 시각·앨범 ID 오름차순
     */
    @Transactional(readOnly = true)
    public List<Long> findExpiredAlbumIds(LocalDateTime now, int limit) {
        return albumRepository.findExpiredIds(now, limit);
    }
}
