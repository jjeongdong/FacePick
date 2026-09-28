package com.back.facepick.album.presentation;

import com.back.facepick.album.application.AlbumCommandService;
import com.back.facepick.album.application.AlbumQueryService;
import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.application.dto.result.AlbumDetailResult;
import com.back.facepick.album.application.dto.result.AlbumInviteResult;
import com.back.facepick.album.application.dto.result.AlbumJoinResult;
import com.back.facepick.album.application.dto.result.AlbumResult;
import com.back.facepick.album.presentation.dto.request.AlbumCreateRequest;
import com.back.facepick.album.presentation.dto.request.AlbumJoinRequest;
import com.back.facepick.global.authorization.annotation.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController implements AlbumApiDocs {
    private final AlbumCommandService albumCommandService;
    private final AlbumQueryService albumQueryService;

    @Override
    @PostMapping
    public ResponseEntity<AlbumCreateResult> createAlbum(
            @AuthUser Long userId, @RequestBody AlbumCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(albumCommandService.createAlbum(userId, request.toCommand()));
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<List<AlbumResult>> getMyAlbums(@AuthUser Long userId) {
        return ResponseEntity.ok(albumQueryService.getMyAlbums(userId));
    }

    @Override
    @GetMapping("/{albumId}")
    public ResponseEntity<AlbumDetailResult> getAlbum(@AuthUser Long userId, @PathVariable Long albumId) {
        return ResponseEntity.ok(albumQueryService.getAlbum(userId, albumId));
    }

    @Override
    @GetMapping("/{albumId}/invite")
    public ResponseEntity<AlbumInviteResult> getAlbumInvite(@AuthUser Long userId, @PathVariable Long albumId) {
        return ResponseEntity.ok(albumQueryService.getAlbumInvite(userId, albumId));
    }

    @Override
    @PostMapping("/{albumId}/invite")
    public ResponseEntity<AlbumInviteResult> reissueAlbumInvite(@AuthUser Long userId, @PathVariable Long albumId) {
        return ResponseEntity.ok(albumCommandService.reissueAlbumInvite(userId, albumId));
    }

    @Override
    @PostMapping("/join")
    public ResponseEntity<AlbumJoinResult> joinAlbum(@AuthUser Long userId, @RequestBody AlbumJoinRequest request) {
        return ResponseEntity.ok(albumCommandService.joinAlbum(userId, request.toCommand()));
    }
}
