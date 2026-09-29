package com.back.facepick.person.infrastructure;

import com.back.facepick.person.domain.PersonFaceRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// 네이티브 쿼리를 쓰는 이유: 이 테이블들은 face-worker 가 쓰고 백엔드 엔티티가 아직 없다(vector 컬럼, 인물 조회 API 때 매핑).
// advisory lock 도 JPQL 로 표현할 수 없다.
@Repository
@RequiredArgsConstructor
public class PersonFaceRepositoryImpl implements PersonFaceRepository {

    private final EntityManager entityManager;

    @Override
    public void lockAlbum(Long albumId) {
        // pg_advisory_xact_lock 은 void 를 돌려줘 결과를 매핑할 수 없으므로 FROM 절에서 불러 1 을 받는다.
        entityManager
                .createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(:albumId)")
                .setParameter("albumId", albumId)
                .getSingleResult();
    }

    @Override
    public List<Long> findPersonIdsOfPhotos(Long albumId, Collection<Long> photoIds) {
        List<?> rows = entityManager
                .createNativeQuery(
                        """
                        SELECT DISTINCT person_id FROM faces
                        WHERE album_id = :albumId AND photo_id IN (:photoIds)
                        ORDER BY person_id
                        """)
                .setParameter("albumId", albumId)
                .setParameter("photoIds", photoIds)
                .getResultList();
        return rows.stream().map(row -> ((Number) row).longValue()).toList();
    }

    @Override
    public void deleteFacesOfPhotos(Long albumId, Collection<Long> photoIds) {
        entityManager
                .createNativeQuery("DELETE FROM faces WHERE album_id = :albumId AND photo_id IN (:photoIds)")
                .setParameter("albumId", albumId)
                .setParameter("photoIds", photoIds)
                .executeUpdate();
    }

    @Override
    public void deleteAnalysesOfPhotos(Long albumId, Collection<Long> photoIds) {
        entityManager
                .createNativeQuery("DELETE FROM face_analyses WHERE album_id = :albumId AND photo_id IN (:photoIds)")
                .setParameter("albumId", albumId)
                .setParameter("photoIds", photoIds)
                .executeUpdate();
    }

    @Override
    public void cleanUpPersons(Collection<Long> personIds, LocalDateTime now) {
        entityManager
                .createNativeQuery(
                        """
                        UPDATE persons p
                        SET cover_face_id = (SELECT f.face_id FROM faces f
                                             WHERE f.person_id = p.person_id
                                             ORDER BY f.det_score DESC, f.face_id
                                             LIMIT 1),
                            modified_at = :now
                        WHERE p.person_id IN (:personIds) AND p.cover_face_id IS NULL
                        """)
                .setParameter("now", now)
                .setParameter("personIds", personIds)
                .executeUpdate();
        // 위에서 채우지 못한(남은 얼굴이 없는) 인물이다.
        entityManager
                .createNativeQuery("DELETE FROM persons WHERE person_id IN (:personIds) AND cover_face_id IS NULL")
                .setParameter("personIds", personIds)
                .executeUpdate();
    }
}
