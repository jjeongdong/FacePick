package com.back.facepick.photo.presentation;

import com.back.facepick.photo.application.dto.result.PhotoCompleteResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult;
import com.back.facepick.photo.presentation.dto.request.PhotoUploadRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

// 검증 어노테이션은 여기에만 둔다 (구현 메서드에 두면 HV000151).
@Tag(name = "[사진] 사진 API")
public interface PhotoApiDocs {

    @Operation(
            summary = "업로드 URL 발급",
            description = "파일 해시 목록을 받아 새 파일에는 서명 URL 을, 이미 올라간 파일에는 ALREADY_UPLOADED 를 돌려줍니다."
                    + " 앨범 참여자만, 한 번에 100장까지. 서명 URL 로 PUT 할 때는 응답의 contentType 을 Content-Type 헤더로 붙여야 합니다"
                    + " (다른 사람이 먼저 등록한 파일이면 요청 때 보낸 값과 다를 수 있습니다).")
    ResponseEntity<PhotoUploadResult> createPhotoUploads(
            @Parameter(hidden = true) Long userId, Long albumId, @Valid PhotoUploadRequest request);

    @Operation(
            summary = "업로드 완료",
            description = "스토리지에 파일이 올라갔는지(크기 포함) 확인하고 업로드 완료로 바꿉니다. 업로더만 할 수 있고, 여러 번 불러도 결과가 같습니다."
                    + " 403 PHOTO_NOT_UPLOADER 를 받으면(다른 참여자가 같은 파일을 이어받은 경우) 업로드 URL 을 다시 요청한 뒤 완료하면 됩니다.")
    ResponseEntity<PhotoCompleteResult> completePhoto(@Parameter(hidden = true) Long userId, Long photoId);
}
