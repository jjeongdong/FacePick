package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoPurpose;
import com.back.facepick.photo.domain.PhotoStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoJpaRepository extends JpaRepository<Photo, Long> {
    List<Photo> findAllByAlbumIdAndPurposeAndContentHashIn(
            Long albumId, PhotoPurpose purpose, Collection<String> contentHashes);

    // 삭제용 조회라 행을 잠근다. 두 사람이 같은 사진을 동시에 지우면 뒤 요청이 앞 요청의 커밋을 기다렸다가
    // 이미 지워진 행을 결과에서 빼므로, 0행 DELETE 로 500 이 나지 않고 스펙대로 건너뛴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Photo> findAllByAlbumIdAndPurposeAndIdIn(Long albumId, PhotoPurpose purpose, Collection<Long> photoIds);

    boolean existsByStorageKey(String storageKey);

    @Query(
            """
            select p from Photo p
            where p.albumId = :albumId and p.id in :photoIds
              and p.status = com.back.facepick.photo.domain.PhotoStatus.UPLOADED
              and p.purpose = com.back.facepick.photo.domain.PhotoPurpose.ALBUM
            order by p.id
            """)
    List<Photo> findUploadedByAlbumIdAndIdIn(
            @Param("albumId") Long albumId, @Param("photoIds") Collection<Long> photoIds);

    // status 는 파라미터가 아닌 리터럴로 둔다. 부분 인덱스(WHERE status = 'UPLOADED')는 조건이 리터럴이어야 쓸 수 있어서,
    // 파라미터면 DB 가 공용 실행 계획으로 바꾼 뒤 앨범 사진 전체를 읽어 정렬한다.
    @Query(
            """
            select p from Photo p
            where p.albumId = :albumId and p.status = com.back.facepick.photo.domain.PhotoStatus.UPLOADED
              and p.purpose = com.back.facepick.photo.domain.PhotoPurpose.ALBUM
            order by p.uploadedAt desc, p.id desc
            """)
    List<Photo> findUploadedPage(@Param("albumId") Long albumId, Limit limit);

    // (uploaded_at, photo_id) 가 커서보다 작은 행. 같은 완료 시각은 photo_id 로 이어 간다.
    // 앞의 uploaded_at <= 는 결과를 바꾸지 않는 중복 조건이다. OR 조건만으로는 인덱스 탐색 범위가 되지 않아
    // 커서 위치까지 행을 하나씩 걸러 내므로, 커서 위치에서 바로 탐색을 시작하게 한다.
    @Query(
            """
            select p from Photo p
            where p.albumId = :albumId and p.status = com.back.facepick.photo.domain.PhotoStatus.UPLOADED
              and p.purpose = com.back.facepick.photo.domain.PhotoPurpose.ALBUM
              and p.uploadedAt <= :uploadedAt
              and (p.uploadedAt < :uploadedAt or (p.uploadedAt = :uploadedAt and p.id < :photoId))
            order by p.uploadedAt desc, p.id desc
            """)
    List<Photo> findUploadedPageAfter(
            @Param("albumId") Long albumId,
            @Param("uploadedAt") LocalDateTime uploadedAt,
            @Param("photoId") Long photoId,
            Limit limit);

    Optional<Photo> findByIdAndStatusAndPurpose(Long photoId, PhotoStatus status, PhotoPurpose purpose);

    Optional<Photo> findByAlbumIdAndUploaderIdAndPurpose(Long albumId, Long uploaderId, PhotoPurpose purpose);

    // 네이티브 쿼리를 쓰는 이유: advisory lock 은 JPQL 로 표현할 수 없다. void 를 돌려줘 FROM 절에서 불러 1 을 받는다.
    // 두 int 형식이라 face-worker·얼굴 정리의 앨범 잠금(bigint 한 개)과 키 공간이 겹치지 않는다.
    @Query(
            value =
                    """
                    SELECT 1 FROM pg_advisory_xact_lock(
                        hashtext('photo-selfie'),
                        hashtext(CAST(:albumId AS text) || ':' || CAST(:uploaderId AS text)))
                    """,
            nativeQuery = true)
    Integer lockSelfie(@Param("albumId") Long albumId, @Param("uploaderId") Long uploaderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select p from Photo p
            where p.albumId = :albumId and p.uploaderId = :uploaderId
              and p.purpose = com.back.facepick.photo.domain.PhotoPurpose.SELFIE
            """)
    Optional<Photo> findSelfieForUpdate(@Param("albumId") Long albumId, @Param("uploaderId") Long uploaderId);

    // "내 사진" 용. 목록 쿼리와 같은 정렬·커서에 photo_id 목록 조건을 더한다.
    @Query(
            """
            select p from Photo p
            where p.albumId = :albumId and p.id in :photoIds
              and p.status = com.back.facepick.photo.domain.PhotoStatus.UPLOADED
              and p.purpose = com.back.facepick.photo.domain.PhotoPurpose.ALBUM
            order by p.uploadedAt desc, p.id desc
            """)
    List<Photo> findUploadedPageIn(
            @Param("albumId") Long albumId, @Param("photoIds") Collection<Long> photoIds, Limit limit);

    @Query(
            """
            select p from Photo p
            where p.albumId = :albumId and p.id in :photoIds
              and p.status = com.back.facepick.photo.domain.PhotoStatus.UPLOADED
              and p.purpose = com.back.facepick.photo.domain.PhotoPurpose.ALBUM
              and p.uploadedAt <= :uploadedAt
              and (p.uploadedAt < :uploadedAt or (p.uploadedAt = :uploadedAt and p.id < :photoId))
            order by p.uploadedAt desc, p.id desc
            """)
    List<Photo> findUploadedPageInAfter(
            @Param("albumId") Long albumId,
            @Param("photoIds") Collection<Long> photoIds,
            @Param("uploadedAt") LocalDateTime uploadedAt,
            @Param("photoId") Long photoId,
            Limit limit);
}
