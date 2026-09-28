package com.back.archfixture.good.alpha.presentation;

import com.back.archfixture.good.alpha.application.dto.result.AlphaCreateResult;
import com.back.archfixture.good.alpha.presentation.dto.request.AlphaCreateRequest;
import org.springframework.http.ResponseEntity;

public interface AlphaApiDocs {
    ResponseEntity<AlphaCreateResult> createAlpha(AlphaCreateRequest request);
}
