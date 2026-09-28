package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoCursor;
import com.back.facepick.photo.domain.PhotoRepository;
import com.back.facepick.photo.domain.PhotoStatus;
import com.back.facepick.photo.domain.exception.PhotoNotFoundException;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
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

    @Override
    public List<Photo> findUploadedByAlbumId(Long albumId, int limit) {
        return photoJpaRepository.findByAlbumIdAndStatusOrderByUploadedAtDescIdDesc(
                albumId, PhotoStatus.UPLOADED, Limit.of(limit));
    }

    @Override
    public List<Photo> findUploadedByAlbumIdAfter(Long albumId, PhotoCursor cursor, int limit) {
        return photoJpaRepository.findPageAfter(
                albumId, PhotoStatus.UPLOADED, cursor.uploadedAt(), cursor.photoId(), Limit.of(limit));
    }

    @Override
    public Photo getUploadedById(Long photoId) {
        return photoJpaRepository
                .findByIdAndStatus(photoId, PhotoStatus.UPLOADED)
                .orElseThrow(PhotoNotFoundException::new);
    }
}
