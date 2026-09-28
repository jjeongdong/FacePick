package com.back.facepick.photo.application.dto.command;

import java.util.List;

public record PhotoUploadCommand(List<UploadFile> files) {

    public record UploadFile(String contentHash, Long byteSize, String contentType) {}

    public List<String> contentHashes() {
        return files.stream().map(UploadFile::contentHash).distinct().toList();
    }
}
