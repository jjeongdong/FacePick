package com.back.facepick.photo.application;

import com.back.facepick.album.application.AlbumQueryApi;
import com.back.facepick.album.application.dto.api.AlbumInfo;
import com.back.facepick.photo.application.dto.command.PhotoDeleteCommand;
import com.back.facepick.photo.application.dto.command.PhotoSelfieUploadCommand;
import com.back.facepick.photo.application.dto.command.PhotoUploadCommand;
import com.back.facepick.photo.application.dto.result.PhotoCompleteResult;
import com.back.facepick.photo.application.dto.result.PhotoDeleteResult;
import com.back.facepick.photo.application.dto.result.PhotoSelfieUploadResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult.FileResult;
import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoDeletePolicy;
import com.back.facepick.photo.domain.PhotoPipeline;
import com.back.facepick.photo.domain.PhotoRepository;
import com.back.facepick.photo.domain.PhotoStorage;
import com.back.facepick.photo.domain.PhotoStorageDeletion;
import com.back.facepick.photo.domain.PhotoStorageDeletionRepository;
import com.back.facepick.photo.domain.PhotoUploadPolicy;
import com.back.facepick.photo.domain.event.PhotosDeletedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhotoCommandService {
    private final PhotoRepository photoRepository;
    private final PhotoStorageDeletionRepository photoStorageDeletionRepository;
    private final PhotoStorage photoStorage;
    private final AlbumQueryApi albumQueryApi;
    private final ApplicationEventPublisher eventPublisher;
    private final PhotoPipeline photoPipeline;

    @Transactional
    public PhotoUploadResult createPhotoUploads(Long userId, Long albumId, PhotoUploadCommand command) {
        LocalDateTime now = LocalDateTime.now();
        AlbumInfo album = albumQueryApi.getInfo(albumId);
        PhotoUploadPolicy.validate(albumQueryApi.isMember(albumId, userId), album.expiresAt(), now);

        Map<String, Photo> photosByHash = new HashMap<>();
        for (Photo photo : photoRepository.findAllByAlbumIdAndContentHashes(albumId, command.contentHashes())) {
            photosByHash.put(photo.getContentHash(), photo);
        }
        List<Photo> newPhotos = new ArrayList<>();
        for (PhotoUploadCommand.UploadFile file : command.files()) {
            Photo.validateFile(file.contentType(), file.byteSize());
            Photo photo = photosByHash.get(file.contentHash());
            if (photo == null) {
                Photo created = Photo.create(albumId, userId, file.contentHash(), file.byteSize(), file.contentType());
                photosByHash.put(file.contentHash(), created);
                newPhotos.add(created);
            } else if (!photo.isUploaded()) {
                photo.reassignUploader(userId);
            }
        }
        photoRepository.saveAll(newPhotos);

        Duration expiry = photoStorage.uploadUrlExpiry();
        List<FileResult> files = command.files().stream()
                .map(file -> toFileResult(photosByHash.get(file.contentHash())))
                .toList();
        return new PhotoUploadResult(now.plus(expiry), files);
    }

    @Transactional
    public PhotoCompleteResult completePhoto(Long userId, Long photoId) {
        LocalDateTime now = LocalDateTime.now();
        Photo photo = photoRepository.getById(photoId);
        Long storedByteSize = photoStorage.findObjectSize(photo.getStorageKey()).orElse(null);
        // 이미 완료된 사진이면 false 라 트랜잭션 안에서 할 일(outbox)을 다시 하지 않는다 (앱 재시도에 안전).
        if (photo.complete(userId, storedByteSize, now)) {
            photoPipeline.onCompleted(photo, now);
        }
        return PhotoCompleteResult.from(photo);
    }

    @Transactional
    public PhotoDeleteResult deletePhotos(Long userId, Long albumId, PhotoDeleteCommand command) {
        LocalDateTime now = LocalDateTime.now();
        AlbumInfo album = albumQueryApi.getInfo(albumId);
        // 이 앨범에 없는 ID(이미 지워짐, 다른 앨범 사진)는 조회 결과에서 빠져 건너뛰게 된다.
        List<Photo> photos = photoRepository.findAllByAlbumIdAndIds(albumId, command.distinctPhotoIds());
        PhotoDeletePolicy.validate(
                albumQueryApi.isMember(albumId, userId), album.expiresAt(), now, userId, album.ownerId(), photos);

        photoStorageDeletionRepository.saveAll(photos.stream()
                .map(photo -> PhotoStorageDeletion.create(photo.getStorageKey(), now))
                .toList());
        photoRepository.deleteAll(photos);
        PhotoDeleteResult result = PhotoDeleteResult.from(photos);
        // person BC 가 같은 트랜잭션에서 이 사진들의 얼굴 데이터를 지운다.
        if (!photos.isEmpty()) {
            eventPublisher.publishEvent(new PhotosDeletedEvent(albumId, result.deletedPhotoIds()));
        }
        return result;
    }

    @Transactional
    public PhotoSelfieUploadResult createSelfieUpload(Long userId, Long albumId, PhotoSelfieUploadCommand command) {
        LocalDateTime now = LocalDateTime.now();
        AlbumInfo album = albumQueryApi.getInfo(albumId);
        PhotoUploadPolicy.validate(albumQueryApi.isMember(albumId, userId), album.expiresAt(), now);
        Photo.validateSelfieFile(command.contentType(), command.byteSize(), command.faceAnalysisConsent());

        photoRepository.lockSelfie(albumId, userId);
        Optional<Photo> current = photoRepository.findSelfieForUpdate(albumId, userId);
        if (current.isPresent() && current.orElseThrow().hasFile(command.contentHash())) {
            Photo selfie = current.orElseThrow();
            return selfie.isUploaded()
                    ? PhotoSelfieUploadResult.alreadyUploaded(selfie)
                    : selfieUploadResult(selfie, PhotoSelfieUploadResult.Status.RESUMED, now);
        }
        current.ifPresent(selfie -> discardSelfie(albumId, selfie, now));
        Photo selfie = Photo.createSelfie(
                albumId,
                userId,
                command.contentHash(),
                command.byteSize(),
                command.contentType(),
                command.faceAnalysisConsent());
        photoRepository.saveAll(List.of(selfie));
        return selfieUploadResult(selfie, PhotoSelfieUploadResult.Status.NEW, now);
    }

    @Transactional
    public void deleteSelfie(Long userId, Long albumId) {
        LocalDateTime now = LocalDateTime.now();
        PhotoDeletePolicy.validateSelfieDelete(albumQueryApi.isMember(albumId, userId));
        photoRepository.lockSelfie(albumId, userId);
        photoRepository.findSelfieForUpdate(albumId, userId).ifPresent(selfie -> discardSelfie(albumId, selfie, now));
    }

    // 사진 삭제와 같은 정리(스토리지 대기열, 얼굴 데이터 정리 이벤트)를 하되, 같은 트랜잭션에서 새 셀피를 넣을 수 있게 바로 지운다.
    private void discardSelfie(Long albumId, Photo selfie, LocalDateTime now) {
        photoStorageDeletionRepository.saveAll(List.of(PhotoStorageDeletion.create(selfie.getStorageKey(), now)));
        photoRepository.deleteAndFlush(selfie);
        eventPublisher.publishEvent(new PhotosDeletedEvent(albumId, List.of(selfie.getId())));
    }

    private PhotoSelfieUploadResult selfieUploadResult(
            Photo selfie, PhotoSelfieUploadResult.Status status, LocalDateTime now) {
        String uploadUrl = photoStorage
                .createUploadUrl(selfie.getStorageKey(), selfie.getContentType())
                .toString();
        return PhotoSelfieUploadResult.of(selfie, status, uploadUrl, now.plus(photoStorage.uploadUrlExpiry()));
    }

    private FileResult toFileResult(Photo photo) {
        if (photo.isUploaded()) {
            return FileResult.alreadyUploaded(photo);
        }
        String uploadUrl = photoStorage
                .createUploadUrl(photo.getStorageKey(), photo.getContentType())
                .toString();
        return FileResult.uploadRequired(photo, uploadUrl);
    }
}
