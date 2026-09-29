#!/usr/bin/env bash
# 분석 완료 시간(S4) 측정: upload.js 를 켜기 **직전에** 실행해, 그 뒤 처음 생긴 앨범의 사진이 모두 분석될 때까지 기다린다.
# 시작·끝을 모두 이 맥의 시계로 잰다 (face_analyses.analyzed_at 은 워커 노트북 시계라 섞지 않는다).
# 사용법: ./wait_analyzed.sh <분석될 사진 수>
set -euo pipefail

expected=${1:?분석될 사진 수를 넘겨주세요}
compose_file="$(cd "$(dirname "$0")/../infra" && pwd)/docker-compose.yml"

sql() {
  docker compose -f "$compose_file" exec -T postgres psql -U facepick -d facepick -tAc "$1"
}

base_album=$(sql "SELECT coalesce(max(album_id), 0) FROM albums")
SECONDS=0
echo "기준 앨범 ID ${base_album}. 이제 upload.js 를 실행하세요."
while true; do
  album=$(sql "SELECT coalesce(min(album_id), 0) FROM albums WHERE album_id > ${base_album}")
  if [ "$album" != "0" ]; then
    analyzed=$(sql "SELECT count(*) FROM face_analyses WHERE album_id = ${album}")
    if [ "$analyzed" -ge "$expected" ]; then
      echo "앨범 ${album}: ${analyzed}장 분석 완료, ${SECONDS}초"
      exit 0
    fi
    if (( SECONDS % 10 == 0 )); then
      echo "앨범 ${album}: ${analyzed}/${expected} (${SECONDS}초)"
    fi
  fi
  sleep 1
done
