package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.AlbumExpiryNotice;
import com.back.facepick.album.domain.AlbumExpiryNoticeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlbumExpiryNoticeRepositoryImpl implements AlbumExpiryNoticeRepository {
    private final AlbumExpiryNoticeJpaRepository albumExpiryNoticeJpaRepository;

    @Override
    public AlbumExpiryNotice save(AlbumExpiryNotice notice) {
        return albumExpiryNoticeJpaRepository.save(notice);
    }

    @Override
    public int createPendingForAlbumsExpiringBetween(LocalDateTime now, LocalDateTime until) {
        return albumExpiryNoticeJpaRepository.insertPendingForAlbumsExpiringBetween(now, until);
    }

    @Override
    public List<AlbumExpiryNotice> findDueForUpdate(LocalDateTime now, int limit) {
        return albumExpiryNoticeJpaRepository.findDueForUpdateSkipLocked(now, limit);
    }

    @Override
    public Optional<AlbumExpiryNotice> findById(Long noticeId) {
        return albumExpiryNoticeJpaRepository.findById(noticeId);
    }
}
