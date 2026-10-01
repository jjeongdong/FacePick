package com.back.facepick.auth.domain;

public interface VerificationMailSender {
    /**
     * 가입 요청 안에서 동기로 보낸다. 사용자가 코드를 받아야 다음 단계로 가므로 실패를 바로 알려야 한다.
     * 주소를 받을 수 없으면 AuthVerificationMailRejectedException, 메일 서비스를 지금 쓸 수 없으면
     * AuthVerificationMailUnavailableException.
     */
    void send(String email, String code);
}
