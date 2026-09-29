package com.back.facepick.photo.application.dto.command;

public record PhotoSelfieUploadCommand(
        String contentHash, Long byteSize, String contentType, boolean faceAnalysisConsent) {}
