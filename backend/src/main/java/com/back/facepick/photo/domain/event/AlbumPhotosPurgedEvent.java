package com.back.facepick.photo.domain.event;

// 만료 앨범의 사진을 모두 지운 트랜잭션 안에서 발행하는 앱 내부 이벤트 (Kafka 로 나가지 않는다).
// 받는 쪽(album·person)은 같은 트랜잭션에서 앨범 행과 남은 얼굴 데이터를 지워야 한다.
public record AlbumPhotosPurgedEvent(Long albumId) {}
