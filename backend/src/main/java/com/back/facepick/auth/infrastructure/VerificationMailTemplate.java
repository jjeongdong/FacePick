package com.back.facepick.auth.infrastructure;

public class VerificationMailTemplate {

    public String subject(String code) {
        return "[facepick] 인증 코드 " + code;
    }

    public String text(String code) {
        return "facepick 가입 인증 코드는 " + code + " 입니다.\n10분 안에 입력해 주세요.";
    }

    public String html(String code) {
        return "<p>facepick 가입 인증 코드</p><p style=\"font-size:24px;font-weight:bold;letter-spacing:4px\">" + code
                + "</p><p>10분 안에 입력해 주세요.</p>";
    }
}
