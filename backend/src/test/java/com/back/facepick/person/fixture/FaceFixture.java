package com.back.facepick.person.fixture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

// faces·persons·face_analyses 는 face-worker(Python)가 쓰는 테이블이라 엔티티가 없어 SQL 로 넣는다.
public final class FaceFixture {

    private static final int DIMENSIONS = 512;
    private static final String EMBEDDING = embedding();

    private FaceFixture() {}

    public static Long insertPerson(JdbcTemplate jdbcTemplate, Long albumId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO persons (album_id, created_at, modified_at) VALUES (?, now(), now()) RETURNING person_id",
                Long.class,
                albumId);
    }

    public static Long insertFace(
            JdbcTemplate jdbcTemplate, Long albumId, Long photoId, Long personId, double detScore) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO faces (photo_id, album_id, person_id, bbox_x1, bbox_y1, bbox_x2, bbox_y2,
                                   det_score, embedding, created_at)
                VALUES (?, ?, ?, 0, 0, 50, 50, ?, CAST(? AS vector), now())
                RETURNING face_id
                """,
                Long.class,
                photoId,
                albumId,
                personId,
                detScore,
                EMBEDDING);
    }

    public static void setCover(JdbcTemplate jdbcTemplate, Long personId, Long faceId) {
        jdbcTemplate.update("UPDATE persons SET cover_face_id = ? WHERE person_id = ?", faceId, personId);
    }

    public static void insertAnalysis(JdbcTemplate jdbcTemplate, Long albumId, Long photoId) {
        jdbcTemplate.update(
                "INSERT INTO face_analyses (photo_id, album_id, face_count, analyzed_at) VALUES (?, ?, 1, now())",
                photoId,
                albumId);
    }

    private static String embedding() {
        List<String> values = new ArrayList<>(Collections.nCopies(DIMENSIONS, "0"));
        values.set(0, "1");
        return "[" + String.join(",", values) + "]";
    }
}
