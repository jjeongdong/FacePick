-- 한 번의 측정에서 앨범 사진이 끝까지 처리됐는지 확인한다.
-- 사용법: docker compose -f ../infra/docker-compose.yml exec -T postgres psql -U facepick -d facepick -v album=<앨범 ID> < results.sql
SELECT count(*)                                                    AS photos,
       count(*) FILTER (WHERE status = 'UPLOADED')                 AS uploaded,
       count(processed_at)                                         AS thumbnails,
       (SELECT count(*) FROM face_analyses WHERE album_id = :album) AS analyzed
FROM photos
WHERE album_id = :album
  AND purpose = 'ALBUM';
