package com.back.facepick.photo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 지운 사진의 스토리지 파일을 스케줄러가 나중에 지우도록 남기는 기록. 수정 시각이 필요 없어 BaseTimeEntity 를 쓰지 않는다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "photo_storage_deletions")
public class PhotoStorageDeletion {
    private static final String ORIGINALS = "/originals/";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deletion_id")
    private Long id;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private PhotoStorageDeletion(String storageKey, LocalDateTime createdAt) {
        this.storageKey = storageKey;
        this.createdAt = createdAt;
    }

    public static PhotoStorageDeletion create(String storageKey, LocalDateTime now) {
        return new PhotoStorageDeletion(storageKey, now);
    }

    public void markDeleted(LocalDateTime now) {
        this.deletedAt = now;
    }

    // 썸네일 워커(storage_keys.derived_keys)와 같은 규칙. 원본 키는 Photo.create 가 만든 형식이다.
    public List<String> storageKeys() {
        int index = storageKey.lastIndexOf(ORIGINALS);
        String albumPrefix = storageKey.substring(0, index);
        String contentHash = storageKey.substring(index + ORIGINALS.length());
        return List.of(
                storageKey,
                albumPrefix + "/thumbnails/" + contentHash + ".jpg",
                albumPrefix + "/previews/" + contentHash + ".jpg");
    }
}
