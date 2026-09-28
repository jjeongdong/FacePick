package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoRepository;
import com.back.facepick.photo.domain.exception.PhotoNotFoundException;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PhotoRepositoryImpl implements PhotoRepository {
    private final PhotoJpaRepository photoJpaRepository;

    @Override
    public Photo getById(Long photoId) {
        return photoJpaRepository.findById(photoId).orElseThrow(PhotoNotFoundException::new);
    }

    @Override
    public List<Photo> saveAll(List<Photo> photos) {
        return photoJpaRepository.saveAll(photos);
    }

    @Override
    public List<Photo> findAllByAlbumIdAndContentHashes(Long albumId, Collection<String> contentHashes) {
        return photoJpaRepository.findAllByAlbumIdAndContentHashIn(albumId, contentHashes);
    }
}
