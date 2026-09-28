package com.back.facepick.album.application;

import com.back.facepick.album.application.dto.command.AlbumCreateCommand;
import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.AlbumRole;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlbumCommandService {
    private final AlbumRepository albumRepository;
    private final AlbumMemberRepository albumMemberRepository;

    @Transactional
    public AlbumCreateResult createAlbum(Long userId, AlbumCreateCommand command) {
        Album album = albumRepository.save(Album.create(userId, command.title(), LocalDateTime.now()));
        albumMemberRepository.save(AlbumMember.create(album, userId, AlbumRole.OWNER));
        return AlbumCreateResult.from(album);
    }
}
