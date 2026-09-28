package com.back.facepick.global.error;

public interface ErrorCode {
    String name();

    ErrorType type();

    String message();
}
