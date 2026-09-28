package com.back.facepick.photo.application;

import com.back.facepick.album.application.AlbumQueryApi;
import com.back.facepick.album.application.dto.api.AlbumInfo;
import com.back.facepick.photo.application.dto.command.PhotoUploadCommand;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult.FileResult;
import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoRepository;
import com.back.facepick.photo.domain.PhotoStorage;
import com.back.facepick.photo.domain.PhotoUploadPolicy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhotoCommandService {
    private final PhotoRepository photoRepository;
    private final PhotoStorage photoStorage;
    private final AlbumQueryApi albumQueryApi;

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
