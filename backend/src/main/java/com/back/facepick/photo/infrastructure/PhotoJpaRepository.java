package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.Photo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoJpaRepository extends JpaRepository<Photo, Long> {
    List<Photo> findAllByAlbumIdAndContentHashIn(Long albumId, Collection<String> contentHashes);
}
