package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoCursor;
import com.back.facepick.photo.domain.PhotoPurpose;
import com.back.facepick.photo.domain.PhotoRepository;
import com.back.facepick.photo.domain.PhotoStatus;
import com.back.facepick.photo.domain.exception.PhotoNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
        return photoJpaRepository.findAllByAlbumIdAndPurposeAndContentHashIn(
                albumId, PhotoPurpose.ALBUM, contentHashes);
    }

    @Override
    public List<Photo> findUploadedByAlbumId(Long albumId, int limit) {
        return photoJpaRepository.findUploadedPage(albumId, Limit.of(limit));
    }

    @Override
    public List<Photo> findUploadedByAlbumIdAfter(Long albumId, PhotoCursor cursor, int limit) {
        return photoJpaRepository.findUploadedPageAfter(
                albumId, cursor.uploadedAt(), cursor.photoId(), Limit.of(limit));
    }

    @Override
    public Photo getUploadedById(Long photoId) {
        return photoJpaRepository
                .findByIdAndStatusAndPurpose(photoId, PhotoStatus.UPLOADED, PhotoPurpose.ALBUM)
                .orElseThrow(PhotoNotFoundException::new);
    }

    @Override
    public List<Photo> findAllByAlbumIdAndIds(Long albumId, Collection<Long> photoIds) {
        return photoJpaRepository.findAllByAlbumIdAndPurposeAndIdIn(albumId, PhotoPurpose.ALBUM, photoIds);
    }

    @Override
    public List<Photo> findUploadedByAlbumIdAndIds(Long albumId, Collection<Long> photoIds) {
        return photoJpaRepository.findUploadedByAlbumIdAndIdIn(albumId, photoIds);
    }

    @Override
    public void deleteAll(List<Photo> photos) {
        photoJpaRepository.deleteAll(photos);
    }

    @Override
    public List<Long> findIdsForPurge(Long albumId, int limit) {
        return photoJpaRepository.findIdsForPurge(albumId, limit);
    }

    @Override
    public void deleteAllByIds(Collection<Long> photoIds) {
        photoJpaRepository.deleteAllByIdIn(photoIds);
    }

    @Override
    public boolean existsByStorageKey(String storageKey) {
        return photoJpaRepository.existsByStorageKey(storageKey);
    }

    @Override
    public Optional<Photo> findSelfie(Long albumId, Long uploaderId) {
        return photoJpaRepository.findByAlbumIdAndUploaderIdAndPurpose(albumId, uploaderId, PhotoPurpose.SELFIE);
    }

    @Override
    public void lockSelfie(Long albumId, Long uploaderId) {
        photoJpaRepository.lockSelfie(albumId, uploaderId);
    }

    @Override
    public Optional<Photo> findSelfieForUpdate(Long albumId, Long uploaderId) {
        return photoJpaRepository.findSelfieForUpdate(albumId, uploaderId);
    }

    @Override
    public List<Photo> findUploadedByAlbumIdIn(Long albumId, Collection<Long> photoIds, int limit) {
        return photoJpaRepository.findUploadedPageIn(albumId, photoIds, Limit.of(limit));
    }

    @Override
    public List<Photo> findUploadedByAlbumIdInAfter(
            Long albumId, Collection<Long> photoIds, PhotoCursor cursor, int limit) {
        return photoJpaRepository.findUploadedPageInAfter(
                albumId, photoIds, cursor.uploadedAt(), cursor.photoId(), Limit.of(limit));
    }

    @Override
    public void deleteAndFlush(Photo photo) {
        photoJpaRepository.delete(photo);
        photoJpaRepository.flush();
    }
}
