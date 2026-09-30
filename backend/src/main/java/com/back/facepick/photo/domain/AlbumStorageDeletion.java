package com.back.facepick.photo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// DB 정리를 마친 만료 앨범의 스토리지 prefix 를 스케줄러가 나중에 지우도록 남기는 기록. 앨범당 한 행.
// 행은 AlbumStorageDeletionRepository.saveIfAbsent(네이티브 ON CONFLICT)로만 만든다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "album_storage_deletions")
public class AlbumStorageDeletion {

    @Id
    @Column(name = "album_id")
    private Long albumId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void markDeleted(LocalDateTime now) {
        this.deletedAt = now;
    }

    // Photo 의 저장 키(albums/{albumId}/originals/...)·워커의 썸네일·미리보기 키가 모두 이 아래다.
    // '/' 로 끝나야 albums/7 정리가 albums/77/ 을 건드리지 않는다.
    public String storagePrefix() {
        return "albums/" + albumId + "/";
    }
}
