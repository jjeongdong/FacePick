package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.exception.AlbumInviteNotFoundException;
import com.back.facepick.album.domain.exception.AlbumNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlbumRepositoryImpl implements AlbumRepository {
    private final AlbumJpaRepository albumJpaRepository;

    @Override
    public Album save(Album album) {
        return albumJpaRepository.save(album);
    }

    @Override
    public Album getById(Long albumId) {
        return albumJpaRepository.findById(albumId).orElseThrow(AlbumNotFoundException::new);
    }

    @Override
    public Album getByInviteCode(String inviteCode) {
        return albumJpaRepository.findByInviteCode(inviteCode).orElseThrow(AlbumInviteNotFoundException::new);
    }
}
