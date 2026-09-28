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
                    + " 앨범 참여자만, 한 번에 100장까지. 서명 URL 로 PUT 할 때는 요청에 보낸 Content-Type 을 그대로 붙여야 합니다.")
    ResponseEntity<PhotoUploadResult> createPhotoUploads(
            @Parameter(hidden = true) Long userId, Long albumId, @Valid PhotoUploadRequest request);

    @Operation(
            summary = "업로드 완료",
            description = "스토리지에 파일이 올라갔는지(크기 포함) 확인하고 업로드 완료로 바꿉니다. 업로더만 할 수 있고, 여러 번 불러도 결과가 같습니다.")
    ResponseEntity<PhotoCompleteResult> completePhoto(@Parameter(hidden = true) Long userId, Long photoId);
}
