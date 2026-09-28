package com.back.facepick.album.domain;

import com.back.facepick.album.domain.exception.AlbumExpiredException;
import com.back.facepick.album.domain.exception.AlbumInvalidTitleException;
import com.back.facepick.album.domain.exception.AlbumNotOwnerException;
import com.back.facepick.global.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "albums")
public class Album extends BaseTimeEntity {
    // PRD 결정: 저장 비용과 얼굴 데이터 보관을 줄이려고 앨범은 30일 뒤 삭제한다.
    public static final int RETENTION_DAYS = 30;
    private static final int MAX_TITLE_LENGTH = 50;
    private static final int INVITE_CODE_LENGTH = 22;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "album_id")
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "invite_code", nullable = false, length = INVITE_CODE_LENGTH)
    private String inviteCode;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private Album(Long ownerId, String title, String inviteCode, LocalDateTime expiresAt) {
        this.ownerId = ownerId;
        this.title = title;
        this.inviteCode = inviteCode;
        this.expiresAt = expiresAt;
    }

    public static Album create(Long ownerId, String title, LocalDateTime now, String inviteCode) {
        if (title == null || title.isBlank() || title.strip().length() > MAX_TITLE_LENGTH) {
            throw new AlbumInvalidTitleException();
        }
        return new Album(ownerId, title.strip(), inviteCode, now.plusDays(RETENTION_DAYS));
    }

    // 링크가 원치 않는 곳에 퍼졌을 때 막는 수단이라 앨범장만 할 수 있다.
    public void reissueInviteCode(Long userId, String newInviteCode) {
        if (!ownerId.equals(userId)) {
            throw new AlbumNotOwnerException();
        }
        this.inviteCode = newInviteCode;
    }

    public AlbumMember join(Long userId, LocalDateTime now) {
        if (!now.isBefore(expiresAt)) {
            throw new AlbumExpiredException();
        }
        return AlbumMember.create(this, userId, AlbumRole.MEMBER);
    }
}
