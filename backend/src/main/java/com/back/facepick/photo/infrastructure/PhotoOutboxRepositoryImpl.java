package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.PhotoOutbox;
import com.back.facepick.photo.domain.PhotoOutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PhotoOutboxRepositoryImpl implements PhotoOutboxRepository {
    private final PhotoOutboxJpaRepository photoOutboxJpaRepository;

    @Override
    public PhotoOutbox save(PhotoOutbox outbox) {
        return photoOutboxJpaRepository.save(outbox);
    }

    @Override
    public List<PhotoOutbox> findUnpublished(int limit) {
        return photoOutboxJpaRepository.findByPublishedAtIsNullOrderByIdAsc(Limit.of(limit));
    }
}
