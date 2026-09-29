package com.back.facepick.photo.application;

import com.back.facepick.album.application.AlbumQueryApi;
import com.back.facepick.album.application.dto.api.AlbumInfo;
import com.back.facepick.global.response.CursorPageResult;
import com.back.facepick.photo.application.dto.command.PhotoDownloadCommand;
import com.back.facepick.photo.application.dto.result.PhotoDetailResult;
import com.back.facepick.photo.application.dto.result.PhotoDownloadResult;
import com.back.facepick.photo.application.dto.result.PhotoSummaryResult;
import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoCursor;
import com.back.facepick.photo.domain.PhotoRepository;
import com.back.facepick.photo.domain.PhotoStorage;
import com.back.facepick.photo.domain.PhotoViewPolicy;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhotoQueryService {
    private final PhotoRepository photoRepository;
    private final PhotoStorage photoStorage;
    private final AlbumQueryApi albumQueryApi;

    @Transactional(readOnly = true)
    public CursorPageResult<PhotoSummaryResult> getPhotos(Long userId, Long albumId, String cursor, int size) {
        LocalDateTime now = LocalDateTime.now();
        validateViewable(userId, albumId, now);

        // 한 장 더 읽어 다음 페이지가 있는지 판단한다 (count 쿼리 없이).
        List<Photo> photos = cursor == null || cursor.isBlank()
                ? photoRepository.findUploadedByAlbumId(albumId, size + 1)
                : photoRepository.findUploadedByAlbumIdAfter(albumId, PhotoCursorCodec.decode(cursor), size + 1);
        boolean hasNext = photos.size() > size;
        List<Photo> page = hasNext ? photos.subList(0, size) : photos;

        LocalDateTime urlExpiresAt = now.plus(photoStorage.downloadUrlExpiry());
        List<PhotoSummaryResult> content = page.stream()
                .map(photo -> PhotoSummaryResult.of(
                        photo,
                        photo.isProcessed()
                                ? photoStorage
                                        .createDownloadUrl(photo.getThumbnailKey())
                                        .toString()
                                : null,
                        urlExpiresAt))
                .toList();
        String nextCursor = hasNext ? PhotoCursorCodec.encode(PhotoCursor.from(page.getLast())) : null;
        return new CursorPageResult<>(content, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public PhotoDetailResult getPhoto(Long userId, Long photoId) {
        LocalDateTime now = LocalDateTime.now();
        Photo photo = photoRepository.getUploadedById(photoId);
        // 요청 경로에 앨범이 없으므로 사진이 속한 앨범으로 권한을 본다.
        validateViewable(userId, photo.getAlbumId(), now);

        String previewUrl = photo.isProcessed()
                ? photoStorage.createDownloadUrl(photo.getPreviewKey()).toString()
                : null;
        String originalUrl = photoStorage
                .createDownloadUrl(photo.getStorageKey(), photo.downloadFileName())
                .toString();
        return PhotoDetailResult.of(photo, previewUrl, originalUrl, now.plus(photoStorage.downloadUrlExpiry()));
    }

    @Transactional(readOnly = true)
    public PhotoDownloadResult getDownloads(Long userId, Long albumId, PhotoDownloadCommand command) {
        LocalDateTime now = LocalDateTime.now();
        validateViewable(userId, albumId, now);

        List<PhotoDownloadResult.Item> photos =
                photoRepository.findUploadedByAlbumIdAndIds(albumId, command.photoIds()).stream()
                        .map(photo -> PhotoDownloadResult.Item.of(
                                photo,
                                photoStorage
                                        .createDownloadUrl(photo.getStorageKey(), photo.downloadFileName())
                                        .toString()))
                        .toList();
        return new PhotoDownloadResult(photos, now.plus(photoStorage.downloadUrlExpiry()));
    }

    // 없는 앨범이면 getInfo 가 album BC 의 404 를 던진다 (참여 여부보다 먼저).
    private void validateViewable(Long userId, Long albumId, LocalDateTime now) {
        AlbumInfo album = albumQueryApi.getInfo(albumId);
        PhotoViewPolicy.validate(albumQueryApi.isMember(albumId, userId), album.expiresAt(), now);
    }
}
