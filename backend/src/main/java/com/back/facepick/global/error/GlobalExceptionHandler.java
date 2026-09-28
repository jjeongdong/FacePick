package com.back.facepick.global.error;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String MALFORMED_BODY_MESSAGE = "요청 본문 형식이 올바르지 않습니다.";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.type().isServerFault()) {
            log.error("서버 원인 비즈니스 예외 code={}", errorCode.name(), e);
        }
        return toResponse(errorCode, errorCode.message());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(GlobalErrorCode.INVALID_INPUT.message());
        return toResponse(GlobalErrorCode.INVALID_INPUT, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return toResponse(GlobalErrorCode.INVALID_INPUT, MALFORMED_BODY_MESSAGE);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleInvalidParameter(Exception e) {
        return toResponse(GlobalErrorCode.INVALID_INPUT, GlobalErrorCode.INVALID_INPUT.message());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return toResponse(GlobalErrorCode.NOT_FOUND, GlobalErrorCode.NOT_FOUND.message());
    }

    // 405 는 HTTP 전용 개념이라 ErrorType 매핑을 거치지 않고 여기서 상태를 정한다.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.from(GlobalErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return toResponse(GlobalErrorCode.FORBIDDEN, GlobalErrorCode.FORBIDDEN.message());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        // 업로드 용량 초과(413)·Content-Type 불일치(415) 처럼 Spring 이 상태를 정해 둔 요청 오류는 서버 장애가 아니다.
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(springError.getStatusCode())
                    .body(ErrorResponse.from(GlobalErrorCode.INVALID_INPUT));
        }
        log.error("처리되지 않은 예외", e);
        return toResponse(GlobalErrorCode.INTERNAL_SERVER_ERROR, GlobalErrorCode.INTERNAL_SERVER_ERROR.message());
    }

    private static ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, String message) {
        return ResponseEntity.status(ErrorHttpStatus.of(errorCode.type())).body(ErrorResponse.of(errorCode, message));
    }
}
