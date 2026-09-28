package com.back.archfixture.good.alpha.presentation;

import com.back.archfixture.good.alpha.application.AlphaCommandService;
import com.back.archfixture.good.alpha.application.dto.result.AlphaCreateResult;
import com.back.archfixture.good.alpha.presentation.dto.request.AlphaCreateRequest;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class AlphaController implements AlphaApiDocs {
    private final AlphaCommandService alphaCommandService;

    public AlphaController(AlphaCommandService alphaCommandService) {
        this.alphaCommandService = alphaCommandService;
    }

    @Override
    public ResponseEntity<AlphaCreateResult> createAlpha(AlphaCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(alphaCommandService.createAlpha(request.toCommand(), LocalDateTime.now()));
    }
}
