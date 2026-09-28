package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.exception.AlbumNotMemberException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlbumMemberRepositoryImpl implements AlbumMemberRepository {
    private final AlbumMemberJpaRepository albumMemberJpaRepository;

    @Override
    public AlbumMember save(AlbumMember albumMember) {
        return albumMemberJpaRepository.save(albumMember);
    }

    @Override
    public AlbumMember getByAlbumIdAndUserId(Long albumId, Long userId) {
        return albumMemberJpaRepository
                .findByAlbumIdAndUserId(albumId, userId)
                .orElseThrow(AlbumNotMemberException::new);
    }

    @Override
    public boolean existsByAlbumIdAndUserId(Long albumId, Long userId) {
        return albumMemberJpaRepository.existsByAlbumIdAndUserId(albumId, userId);
    }

    @Override
    public List<AlbumMember> findAllByUserIdWithAlbum(Long userId) {
        return albumMemberJpaRepository.findAllByUserIdWithAlbum(userId);
    }
}
