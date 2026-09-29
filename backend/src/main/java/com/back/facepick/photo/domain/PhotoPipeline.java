package com.back.facepick.photo.domain;

import java.time.LocalDateTime;

// 업로드 완료 뒤 사진 처리(썸네일 → 얼굴 분석)를 워커에 넘기는 방식을 감춘 포트.
// facepick.pipeline.mode 로 이벤트(Kafka)·동기(HTTP) 구현 중 하나만 등록된다.
public interface PhotoPipeline {

    // 완료 트랜잭션 안에서, 이번 요청으로 새로 완료된 사진에만 부른다.
    void onCompleted(Photo photo, LocalDateTime now);

    // 완료 트랜잭션이 커밋된 뒤 완료 요청마다 부른다. 처리를 넘기지 못하면 예외를 던진다.
    void afterCommit(Long photoId);
}
