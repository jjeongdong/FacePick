package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.back.facepick.global.config.data.JpaAuditingConfig;
import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoCursor;
import com.back.facepick.photo.domain.exception.PhotoNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, PhotoRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class PhotoRepositoryImplQueryTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);
    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private PhotoRepositoryImpl photoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("해시 목록 조회 - 같은 앨범에서 목록에 있는 해시만 돌려준다")
    void findsByAlbumAndHashes() {
        // given
        photoRepository.saveAll(List.of(
                Photo.create(1L, 1L, HASH_A, 1000L, "image/jpeg"),
                Photo.create(1L, 1L, HASH_B, 1000L, "image/jpeg"),
                Photo.create(2L, 1L, HASH_A, 1000L, "image/jpeg")));
        entityManager.flush();
        entityManager.clear();

        // when
        List<Photo> photos = photoRepository.findAllByAlbumIdAndContentHashes(1L, List.of(HASH_A, HASH_C));

        // then
        assertThat(photos).extracting(Photo::getAlbumId, Photo::getContentHash).containsExactly(tuple(1L, HASH_A));
    }

    @Test
    @DisplayName("단건 조회 - 없으면 PhotoNotFoundException")
    void throwsWhenMissing() {
        // when & then
        assertThatThrownBy(() -> photoRepository.getById(999L)).isInstanceOf(PhotoNotFoundException.class);
    }

    @Test
    @DisplayName("첫 페이지 조회 - 완료 시각 최신 순으로, PENDING·다른 앨범은 빼고 limit 만큼")
    void findsFirstPage() {
        // given
        Photo old = uploaded(1L, hash(1), T.minusHours(2));
        Photo recent = uploaded(1L, hash(2), T);
        Photo middle = uploaded(1L, hash(3), T.minusHours(1));
        photoRepository.saveAll(List.of(
                old,
                recent,
                middle,
                Photo.create(1L, 1L, hash(4), 1000L, "image/jpeg"),
                uploaded(2L, hash(5), T.plusHours(1))));
        flushAndClear();

        // when
        List<Photo> photos = photoRepository.findUploadedByAlbumId(1L, 2);

        // then
        assertThat(photos).extracting(Photo::getId).containsExactly(recent.getId(), middle.getId());
    }

    @Test
    @DisplayName("커서 이후 조회 - 같은 완료 시각이면 photo_id 로 이어서, 겹치거나 빠지지 않는다")
    void continuesAfterCursorWithSameUploadedAt() {
        // given
        Photo first = uploaded(1L, hash(1), T);
        Photo second = uploaded(1L, hash(2), T);
        Photo third = uploaded(1L, hash(3), T);
        Photo older = uploaded(1L, hash(4), T.minusMinutes(1));
        photoRepository.saveAll(List.of(first, second, third, older));
        flushAndClear();
        List<Photo> firstPage = photoRepository.findUploadedByAlbumId(1L, 2);

        // when
        List<Photo> nextPage = photoRepository.findUploadedByAlbumIdAfter(1L, PhotoCursor.from(firstPage.getLast()), 2);

        // then
        assertThat(firstPage).extracting(Photo::getId).containsExactly(third.getId(), second.getId());
        assertThat(nextPage).extracting(Photo::getId).containsExactly(first.getId(), older.getId());
    }

    @Test
    @DisplayName("업로드 사진 단건 조회 - PENDING 이면 PhotoNotFoundException")
    void throwsForPendingPhoto() {
        // given
        Photo pending = Photo.create(1L, 1L, hash(1), 1000L, "image/jpeg");
        photoRepository.saveAll(List.of(pending));
        flushAndClear();

        // when & then
        assertThatThrownBy(() -> photoRepository.getUploadedById(pending.getId()))
                .isInstanceOf(PhotoNotFoundException.class);
    }

    @Test
    @DisplayName("업로드 사진 단건 조회 - 워커가 쓴 컬럼을 읽는다")
    void readsWorkerColumns() {
        // given
        Photo photo = uploaded(1L, hash(1), T);
        photoRepository.saveAll(List.of(photo));
        entityManager.flush();
        writeWorkerColumns(photo.getId());
        entityManager.clear();

        // when
        Photo found = photoRepository.getUploadedById(photo.getId());

        // then
        assertThat(found.getThumbnailKey()).isEqualTo("albums/1/thumbnails/x.jpg");
        assertThat(found.getPreviewKey()).isEqualTo("albums/1/previews/x.jpg");
        assertThat(found.getWidth()).isEqualTo(4032);
        assertThat(found.getHeight()).isEqualTo(3024);
        assertThat(found.getTakenAt()).isEqualTo(LocalDateTime.of(2026, 8, 30, 10, 0));
        assertThat(found.isProcessed()).isTrue();
    }

    @Test
    @DisplayName("워커 컬럼 보존 - 백엔드가 사진을 UPDATE 해도 워커 값이 지워지지 않는다")
    void keepsWorkerColumnsOnUpdate() {
        // given
        Photo photo = Photo.create(1L, 1L, hash(1), 1000L, "image/jpeg");
        photoRepository.saveAll(List.of(photo));
        flushAndClear();
        // 워커 값이 없을 때 읽어 둔 엔티티를, 워커가 쓴 뒤에 고치는 순서를 재현한다.
        Photo loaded = photoRepository.getById(photo.getId());
        writeWorkerColumns(photo.getId());

        // when
        loaded.reassignUploader(2L);
        flushAndClear();

        // then
        Photo reloaded = photoRepository.getById(photo.getId());
        assertThat(reloaded.getUploaderId()).isEqualTo(2L);
        assertThat(reloaded.getThumbnailKey()).isEqualTo("albums/1/thumbnails/x.jpg");
    }

    @Test
    @DisplayName("앨범·ID 조회 - 이 앨범에 있는 사진만, 상태와 관계없이 돌려준다")
    void findsByAlbumAndIds() {
        // given
        Photo uploaded = uploaded(1L, hash(1), T);
        Photo pending = Photo.create(1L, 1L, hash(2), 1000L, "image/jpeg");
        Photo otherAlbum = uploaded(2L, hash(3), T);
        photoRepository.saveAll(List.of(uploaded, pending, otherAlbum));
        flushAndClear();

        // when
        List<Photo> photos = photoRepository.findAllByAlbumIdAndIds(
                1L, List.of(uploaded.getId(), pending.getId(), otherAlbum.getId(), 999L));

        // then
        assertThat(photos).extracting(Photo::getId).containsExactlyInAnyOrder(uploaded.getId(), pending.getId());
    }

    @Test
    @DisplayName("앨범·ID 업로드 사진 조회 - 이 앨범의 업로드 완료 사진만 photo_id 오름차순으로 돌려준다")
    void findsUploadedByAlbumAndIds() {
        // given
        Photo second = uploaded(1L, hash(1), T);
        Photo first = uploaded(1L, hash(2), T.minusHours(1));
        Photo pending = Photo.create(1L, 1L, hash(3), 1000L, "image/jpeg");
        Photo otherAlbum = uploaded(2L, hash(4), T);
        photoRepository.saveAll(List.of(first, second, pending, otherAlbum));
        flushAndClear();

        // when
        List<Photo> photos = photoRepository.findUploadedByAlbumIdAndIds(
                1L, List.of(second.getId(), first.getId(), pending.getId(), otherAlbum.getId(), 999L));

        // then
        assertThat(photos).extracting(Photo::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    @DisplayName("삭제 후 재저장 - 지운 사진과 같은 해시로 다시 올릴 수 있다")
    void allowsSameHashAfterDelete() {
        // given
        Photo photo = uploaded(1L, hash(1), T);
        photoRepository.saveAll(List.of(photo));
        flushAndClear();
        photoRepository.deleteAll(photoRepository.findAllByAlbumIdAndIds(1L, List.of(photo.getId())));
        flushAndClear();

        // when
        Photo again = Photo.create(1L, 2L, hash(1), 1000L, "image/jpeg");
        photoRepository.saveAll(List.of(again));
        flushAndClear();

        // then
        assertThat(photoRepository.findAllByAlbumIdAndContentHashes(1L, List.of(hash(1))))
                .extracting(Photo::getId)
                .containsExactly(again.getId());
    }

    @Test
    @DisplayName("저장 키 존재 확인 - 같은 키의 사진이 있으면 true, 지운 뒤에는 false")
    void checksStorageKeyExists() {
        // given
        Photo photo = uploaded(1L, hash(1), T);
        photoRepository.saveAll(List.of(photo));
        flushAndClear();
        String storageKey = photo.getStorageKey();

        // when
        boolean existsBefore = photoRepository.existsByStorageKey(storageKey);
        photoRepository.deleteAll(photoRepository.findAllByAlbumIdAndIds(1L, List.of(photo.getId())));
        flushAndClear();
        boolean existsAfter = photoRepository.existsByStorageKey(storageKey);

        // then
        assertThat(existsBefore).isTrue();
        assertThat(existsAfter).isFalse();
    }

    private static Photo uploaded(Long albumId, String hash, LocalDateTime uploadedAt) {
        Photo photo = Photo.create(albumId, 1L, hash, 1000L, "image/jpeg");
        photo.complete(1L, 1000L, uploadedAt);
        return photo;
    }

    private static String hash(int number) {
        return String.format("%064x", number);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    // 썸네일 워커가 하는 UPDATE 를 그대로 흉내 낸다 (엔티티에서는 쓸 수 없는 컬럼).
    private void writeWorkerColumns(Long photoId) {
        entityManager
                .getEntityManager()
                .createNativeQuery("UPDATE photos SET thumbnail_key = 'albums/1/thumbnails/x.jpg',"
                        + " preview_key = 'albums/1/previews/x.jpg', width = 4032, height = 3024,"
                        + " taken_at = TIMESTAMP '2026-08-30 10:00:00', processed_at = TIMESTAMP '2026-09-01 12:01:00'"
                        + " WHERE photo_id = :photoId")
                .setParameter("photoId", photoId)
                .executeUpdate();
    }
}
