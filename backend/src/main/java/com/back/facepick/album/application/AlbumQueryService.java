package com.back.facepick.album.application;

import com.back.facepick.album.application.dto.result.AlbumDetailResult;
import com.back.facepick.album.application.dto.result.AlbumInviteResult;
import com.back.facepick.album.application.dto.result.AlbumResult;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.user.application.UserQueryApi;
import com.back.facepick.user.application.dto.api.UserInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlbumQueryService {
    private final AlbumRepository albumRepository;
    private final AlbumMemberRepository albumMemberRepository;
    private final UserQueryApi userQueryApi;

    @Transactional(readOnly = true)
    public AlbumDetailResult getAlbum(Long userId, Long albumId) {
        Album album = albumRepository.getById(albumId);
        // 참여자가 아니면 여기서 AlbumNotMemberException(403)이 난다. 없는 앨범은 위에서 404 가 먼저 난다.
        albumMemberRepository.getByAlbumIdAndUserId(albumId, userId);
        UserInfo owner = userQueryApi.getInfo(album.getOwnerId());
        return AlbumDetailResult.of(album, owner.nickname());
    }

    @Transactional(readOnly = true)
    public List<AlbumResult> getMyAlbums(Long userId) {
        return albumMemberRepository.findAllByUserIdWithAlbum(userId).stream()
                .map(AlbumResult::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlbumInviteResult getAlbumInvite(Long userId, Long albumId) {
        Album album = albumRepository.getById(albumId);
        // 참여자가 아니면 AlbumNotMemberException(403). 없는 앨범은 위에서 404 가 먼저 난다.
        albumMemberRepository.getByAlbumIdAndUserId(albumId, userId);
        return AlbumInviteResult.from(album);
    }
}
