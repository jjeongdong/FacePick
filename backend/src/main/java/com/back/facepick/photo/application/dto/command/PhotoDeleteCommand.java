package com.back.facepick.photo.application.dto.command;

import java.util.List;

public record PhotoDeleteCommand(List<Long> photoIds) {

    public List<Long> distinctPhotoIds() {
        return photoIds.stream().distinct().toList();
    }
}
