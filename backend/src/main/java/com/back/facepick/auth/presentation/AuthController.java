package com.back.facepick.auth.presentation;

import com.back.facepick.auth.application.AuthCommandService;
import com.back.facepick.auth.application.dto.result.LoginResult;
import com.back.facepick.auth.application.dto.result.SignUpResult;
import com.back.facepick.auth.application.dto.result.TokenReissueResult;
import com.back.facepick.auth.presentation.dto.request.LoginRequest;
import com.back.facepick.auth.presentation.dto.request.LogoutRequest;
import com.back.facepick.auth.presentation.dto.request.SignUpRequest;
import com.back.facepick.auth.presentation.dto.request.TokenReissueRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApiDocs {
    private final AuthCommandService authCommandService;

    @Override
    @PostMapping("/signup")
    public ResponseEntity<SignUpResult> signUp(@RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authCommandService.signUp(request.toCommand()));
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<LoginResult> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authCommandService.login(request.toCommand()));
    }

    @Override
    @PostMapping("/reissue")
    public ResponseEntity<TokenReissueResult> reissueToken(@RequestBody TokenReissueRequest request) {
        return ResponseEntity.ok(authCommandService.reissueToken(request.toCommand()));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest request) {
        authCommandService.logout(request.toCommand());
        return ResponseEntity.noContent().build();
    }
}
