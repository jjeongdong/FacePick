package com.back.facepick.photo.presentation;

import com.back.facepick.global.authorization.annotation.AuthUser;
import com.back.facepick.global.response.CursorPageResult;
import com.back.facepick.photo.application.PhotoCommandService;
import com.back.facepick.photo.application.PhotoQueryService;
import com.back.facepick.photo.application.dto.result.PhotoCompleteResult;
import com.back.facepick.photo.application.dto.result.PhotoDeleteResult;
import com.back.facepick.photo.application.dto.result.PhotoDetailResult;
import com.back.facepick.photo.application.dto.result.PhotoSummaryResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult;
import com.back.facepick.photo.presentation.dto.request.PhotoDeleteRequest;
import com.back.facepick.photo.presentation.dto.request.PhotoListRequest;
import com.back.facepick.photo.presentation.dto.request.PhotoUploadRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/albums/{albumId}/photos 와 /api/photos 두 경로에 걸쳐 클래스 매핑은 /api 까지만 둔다.
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PhotoController implements PhotoApiDocs {
    private final PhotoCommandService photoCommandService;
    private final PhotoQueryService photoQueryService;

    // 새로 만든 사진과 건너뛴 사진이 섞인 결과라 201 이 아닌 200 으로 준다.
    @Override
    @PostMapping("/albums/{albumId}/photos/uploads")
    public ResponseEntity<PhotoUploadResult> createPhotoUploads(
            @AuthUser Long userId, @PathVariable Long albumId, @RequestBody PhotoUploadRequest request) {
        return ResponseEntity.ok(photoCommandService.createPhotoUploads(userId, albumId, request.toCommand()));
    }

    @Override
    @PostMapping("/photos/{photoId}/complete")
    public ResponseEntity<PhotoCompleteResult> completePhoto(@AuthUser Long userId, @PathVariable Long photoId) {
        return ResponseEntity.ok(photoCommandService.completePhoto(userId, photoId));
    }

    // 여러 장을 한 번에 지우는 동작이라 DELETE 대신 POST + 동사 하위 경로를 쓴다.
    @Override
    @PostMapping("/albums/{albumId}/photos/delete")
    public ResponseEntity<PhotoDeleteResult> deletePhotos(
            @AuthUser Long userId, @PathVariable Long albumId, @RequestBody PhotoDeleteRequest request) {
        return ResponseEntity.ok(photoCommandService.deletePhotos(userId, albumId, request.toCommand()));
    }

    @Override
    @GetMapping("/albums/{albumId}/photos")
    public ResponseEntity<CursorPageResult<PhotoSummaryResult>> getPhotos(
            @AuthUser Long userId, @PathVariable Long albumId, PhotoListRequest request) {
        return ResponseEntity.ok(photoQueryService.getPhotos(userId, albumId, request.cursor(), request.size()));
    }

    @Override
    @GetMapping("/photos/{photoId}")
    public ResponseEntity<PhotoDetailResult> getPhoto(@AuthUser Long userId, @PathVariable Long photoId) {
        return ResponseEntity.ok(photoQueryService.getPhoto(userId, photoId));
    }
}
