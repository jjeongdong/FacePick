package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoJpaRepository extends JpaRepository<Photo, Long> {
    List<Photo> findAllByAlbumIdAndContentHashIn(Long albumId, Collection<String> contentHashes);

    List<Photo> findAllByAlbumIdAndIdIn(Long albumId, Collection<Long> photoIds);

    // status 는 파라미터가 아닌 리터럴로 둔다. 부분 인덱스(WHERE status = 'UPLOADED')는 조건이 리터럴이어야 쓸 수 있어서,
    // 파라미터면 DB 가 공용 실행 계획으로 바꾼 뒤 앨범 사진 전체를 읽어 정렬한다.
    @Query(
            """
            select p from Photo p
            where p.albumId = :albumId and p.status = com.back.facepick.photo.domain.PhotoStatus.UPLOADED
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
              and p.uploadedAt <= :uploadedAt
              and (p.uploadedAt < :uploadedAt or (p.uploadedAt = :uploadedAt and p.id < :photoId))
            order by p.uploadedAt desc, p.id desc
            """)
    List<Photo> findUploadedPageAfter(
            @Param("albumId") Long albumId,
            @Param("uploadedAt") LocalDateTime uploadedAt,
            @Param("photoId") Long photoId,
            Limit limit);

    Optional<Photo> findByIdAndStatus(Long photoId, PhotoStatus status);
}
