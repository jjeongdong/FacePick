package com.back.facepick.album.application;

import com.back.facepick.album.application.dto.command.AlbumCreateCommand;
import com.back.facepick.album.application.dto.command.AlbumJoinCommand;
import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.application.dto.result.AlbumInviteResult;
import com.back.facepick.album.application.dto.result.AlbumJoinResult;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.album.domain.InviteCodeGenerator;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlbumCommandService {
    private final AlbumRepository albumRepository;
    private final AlbumMemberRepository albumMemberRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    @Transactional
    public AlbumCreateResult createAlbum(Long userId, AlbumCreateCommand command) {
        Album album = albumRepository.save(
                Album.create(userId, command.title(), LocalDateTime.now(), inviteCodeGenerator.generate()));
        albumMemberRepository.save(AlbumMember.create(album, userId, AlbumRole.OWNER));
        return AlbumCreateResult.from(album);
    }

    @Transactional
    public AlbumInviteResult reissueAlbumInvite(Long userId, Long albumId) {
        Album album = albumRepository.getById(albumId);
        album.reissueInviteCode(userId, inviteCodeGenerator.generate());
        return AlbumInviteResult.from(album);
    }

    @Transactional
    public AlbumJoinResult joinAlbum(Long userId, AlbumJoinCommand command) {
        Album album = albumRepository.getByInviteCode(command.inviteCode());
        // 링크를 다시 누른 참여자(앨범장 포함)는 역할을 바꾸지 않고 그대로 성공시킨다.
        if (!albumMemberRepository.existsByAlbumIdAndUserId(album.getId(), userId)) {
            albumMemberRepository.save(album.join(userId, LocalDateTime.now()));
        }
        return AlbumJoinResult.from(album);
    }
}
