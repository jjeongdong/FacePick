package com.back.facepick.photo.application.dto.command;

import java.util.List;

public record PhotoDownloadCommand(List<Long> photoIds) {}
