package com.back.facepick.user.presentation;

import com.back.facepick.global.authorization.annotation.AuthUser;
import com.back.facepick.user.application.UserQueryService;
import com.back.facepick.user.application.dto.result.UserResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserApiDocs {
    private final UserQueryService userQueryService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<UserResult> getMyUser(@AuthUser Long userId) {
        return ResponseEntity.ok(userQueryService.getMyUser(userId));
    }
}
