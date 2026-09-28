package com.back.facepick.album.presentation;

import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.application.dto.result.AlbumDetailResult;
import com.back.facepick.album.application.dto.result.AlbumResult;
import com.back.facepick.album.presentation.dto.request.AlbumCreateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;

// 검증 어노테이션은 여기에만 둔다 (구현 메서드에 두면 HV000151).
@Tag(name = "[앨범] 앨범 API")
public interface AlbumApiDocs {

    @Operation(summary = "앨범 생성", description = "앨범을 만들고 만든 사람을 앨범장으로 등록합니다. 30일 뒤 만료됩니다. (201)")
    ResponseEntity<AlbumCreateResult> createAlbum(
            @Parameter(hidden = true) Long userId, @Valid AlbumCreateRequest request);

    @Operation(summary = "내 앨범 목록", description = "내가 참여한 앨범을 최근 참여순으로 조회합니다.")
    ResponseEntity<List<AlbumResult>> getMyAlbums(@Parameter(hidden = true) Long userId);

    @Operation(summary = "앨범 조회", description = "앨범 정보를 조회합니다. 참여자만 조회할 수 있습니다.")
    ResponseEntity<AlbumDetailResult> getAlbum(@Parameter(hidden = true) Long userId, Long albumId);
}
