package com.back.facepick.photo.presentation;

import com.back.facepick.global.response.CursorPageResult;
import com.back.facepick.photo.application.dto.result.PhotoCompleteResult;
import com.back.facepick.photo.application.dto.result.PhotoDeleteResult;
import com.back.facepick.photo.application.dto.result.PhotoDetailResult;
import com.back.facepick.photo.application.dto.result.PhotoDownloadResult;
import com.back.facepick.photo.application.dto.result.PhotoSelfieResult;
import com.back.facepick.photo.application.dto.result.PhotoSelfieUploadResult;
import com.back.facepick.photo.application.dto.result.PhotoSummaryResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult;
import com.back.facepick.photo.presentation.dto.request.PhotoDeleteRequest;
import com.back.facepick.photo.presentation.dto.request.PhotoDownloadRequest;
import com.back.facepick.photo.presentation.dto.request.PhotoListRequest;
import com.back.facepick.photo.presentation.dto.request.PhotoSelfieUploadRequest;
import com.back.facepick.photo.presentation.dto.request.PhotoUploadRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
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

    @Operation(
            summary = "앨범 사진 목록",
            description = "업로드가 끝난 사진을 완료 시각 최신 순으로 커서 페이징합니다. 앨범 참여자만, 만료된 앨범은 400."
                    + " thumbnailUrl 은 urlExpiresAt 까지 유효한 서명 URL 이고, 썸네일을 아직 만드는 중인 사진은 null 입니다."
                    + " 다음 페이지는 응답의 nextCursor 를 cursor 로 넘깁니다.")
    ResponseEntity<CursorPageResult<PhotoSummaryResult>> getPhotos(
            @Parameter(hidden = true) Long userId, Long albumId, @ParameterObject @Valid PhotoListRequest request);

    @Operation(
            summary = "사진 상세",
            description =
                    "미리보기(previewUrl)와 원본(originalUrl) 서명 URL 을 줍니다. 원본 URL 로 받으면 facepick-{photoId}.{확장자} 로 저장됩니다."
                            + " 미리보기를 아직 만드는 중이면 previewUrl 은 null 입니다. 앨범 참여자만, 업로드 전 사진은 404.")
    ResponseEntity<PhotoDetailResult> getPhoto(@Parameter(hidden = true) Long userId, Long photoId);

    @Operation(
            summary = "사진 여러 장 삭제",
            description = "직접 올린 사진을, 앨범장은 앨범의 모든 사진을 한 번에 100장까지 삭제합니다."
                    + " 이 앨범에 없는 ID(이미 삭제됨 등)는 건너뛰고 실제로 삭제한 ID 만 돌려줍니다."
                    + " 권한 없는 사진이 하나라도 있으면 아무것도 삭제하지 않고 403 입니다. 삭제는 되돌릴 수 없습니다.")
    ResponseEntity<PhotoDeleteResult> deletePhotos(
            @Parameter(hidden = true) Long userId, Long albumId, @Valid PhotoDeleteRequest request);

    @Operation(
            summary = "사진 여러 장 다운로드 URL",
            description = "선택한 사진들의 원본 서명 URL 을 한 번에 100장까지 줍니다. 각 URL 로 받으면 fileName 으로 저장되고, urlExpiresAt 까지 유효합니다."
                    + " 앨범 참여자만, 만료된 앨범은 400. 이 앨범에 없거나 업로드 전인 ID 는 건너뛰고 받을 수 있는 사진만 photoId 순으로 돌려줍니다.")
    ResponseEntity<PhotoDownloadResult> getDownloads(
            @Parameter(hidden = true) Long userId, Long albumId, @Valid PhotoDownloadRequest request);

    @Operation(
            summary = "내 사진(셀피) 업로드 URL 발급",
            description = "얼굴 분석 동의(faceAnalysisConsent: true)와 함께 셀피 1장을 등록합니다. JPEG·PNG·HEIC·HEIF, 20MB 이하."
                    + " 서명 URL 로 PUT 한 뒤 업로드 완료 API 를 부릅니다. 이미 등록한 셀피와 다른 파일이면 옛 셀피를 지우고 새로 등록합니다."
                    + " 같은 파일이면 올리는 중일 때 RESUMED(URL 재발급), 이미 올라갔으면 ALREADY_UPLOADED 입니다."
                    + " 셀피는 앨범 사진 목록·상세·다운로드에 나오지 않습니다.")
    ResponseEntity<PhotoSelfieUploadResult> createSelfieUpload(
            @Parameter(hidden = true) Long userId, Long albumId, @Valid PhotoSelfieUploadRequest request);

    @Operation(
            summary = "내 사진(셀피) 등록 상태",
            description = "NONE(미등록), UPLOADING(업로드 중), PROCESSING(얼굴 분석 중), NO_FACE(얼굴을 찾지 못함 — 다시 등록),"
                    + " READY(등록 완료, personId 가 나) 중 하나입니다. thumbnailUrl 은 썸네일이 만들어진 뒤에만 있습니다.")
    ResponseEntity<PhotoSelfieResult> getSelfie(@Parameter(hidden = true) Long userId, Long albumId);

    @Operation(
            summary = "내가 나온 사진 목록",
            description = "등록한 셀피의 인물이 나온 앨범 사진을 앨범 사진 목록과 같은 방식으로 커서 페이징합니다."
                    + " 셀피 등록이 READY 가 아니면 409 PHOTO_SELFIE_NOT_READY 입니다.")
    ResponseEntity<CursorPageResult<PhotoSummaryResult>> getMyPhotos(
            @Parameter(hidden = true) Long userId, Long albumId, @ParameterObject @Valid PhotoListRequest request);

    @Operation(summary = "내 사진(셀피) 등록 취소", description = "셀피와 그 얼굴 데이터를 지웁니다. 등록한 셀피가 없어도 204 입니다.")
    ResponseEntity<Void> deleteSelfie(@Parameter(hidden = true) Long userId, Long albumId);
}
