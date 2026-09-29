package com.back.facepick.person.domain;

// 사진 한 장의 분석 결과. personId 는 얼굴이 없으면 null 이고, 얼굴이 여럿이면 가장 먼저 저장된 얼굴의 인물이다
// (셀피는 face-worker 가 얼굴을 하나만 저장한다).
public record PhotoFaceAnalysis(int faceCount, Long personId) {}
