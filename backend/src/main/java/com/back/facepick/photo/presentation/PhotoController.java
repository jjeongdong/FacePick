package com.back.facepick.photo.presentation;

import com.back.facepick.global.authorization.annotation.AuthUser;
import com.back.facepick.photo.application.PhotoCommandService;
import com.back.facepick.photo.application.dto.result.PhotoCompleteResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult;
import com.back.facepick.photo.presentation.dto.request.PhotoUploadRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
}
