package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.PhotoOutbox;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoOutboxJpaRepository extends JpaRepository<PhotoOutbox, Long> {
    List<PhotoOutbox> findByPublishedAtIsNullOrderByIdAsc(Limit limit);
}
