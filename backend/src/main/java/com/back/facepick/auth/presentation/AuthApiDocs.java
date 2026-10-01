package com.back.facepick.auth.presentation;

import com.back.facepick.auth.application.dto.result.LoginResult;
import com.back.facepick.auth.application.dto.result.SignUpResult;
import com.back.facepick.auth.application.dto.result.TokenReissueResult;
import com.back.facepick.auth.presentation.dto.request.EmailVerificationSendRequest;
import com.back.facepick.auth.presentation.dto.request.LoginRequest;
import com.back.facepick.auth.presentation.dto.request.LogoutRequest;
import com.back.facepick.auth.presentation.dto.request.SignUpRequest;
import com.back.facepick.auth.presentation.dto.request.TokenReissueRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

// 검증 어노테이션은 여기에만 둔다 (구현 메서드에 두면 HV000151).
@Tag(name = "[인증] 회원가입·로그인 API")
public interface AuthApiDocs {

    @Operation(
            summary = "가입 인증 코드 발송",
            description = "이메일로 6자리 인증 코드를 보냅니다 (10분 유효, 같은 이메일은 60초에 한 번). "
                    + "(204) 이미 가입 409, 60초 안 재요청 429, 받을 수 없는 주소 400, 메일 서비스 장애 502")
    ResponseEntity<Void> sendEmailVerification(@Valid EmailVerificationSendRequest request);

    @Operation(
            summary = "회원가입",
            description = "이메일·비밀번호·닉네임·인증 코드로 가입하고 토큰을 발급합니다. (201) 코드가 틀리면 400 이며 5번 틀리면 코드를 다시 받아야 합니다.")
    ResponseEntity<SignUpResult> signUp(@Valid SignUpRequest request);

    @Operation(summary = "로그인", description = "이메일·비밀번호로 로그인하고 토큰을 발급합니다.")
    ResponseEntity<LoginResult> login(@Valid LoginRequest request);

    @Operation(summary = "액세스 토큰 재발급", description = "리프레시 토큰으로 새 액세스 토큰을 발급합니다.")
    ResponseEntity<TokenReissueResult> reissueToken(@Valid TokenReissueRequest request);

    @Operation(summary = "로그아웃", description = "리프레시 토큰을 폐기합니다. 이미 폐기된 토큰이어도 204 입니다.")
    ResponseEntity<Void> logout(@Valid LogoutRequest request);
}
