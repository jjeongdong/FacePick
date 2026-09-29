package com.back.facepick.photo.domain.event;

import java.util.List;

// 사진을 지운 트랜잭션 안에서 발행하는 앱 내부 이벤트 (Kafka 로 나가지 않는다). 받는 쪽은 같은 트랜잭션에서 정리해야 한다.
public record PhotosDeletedEvent(Long albumId, List<Long> photoIds) {}
