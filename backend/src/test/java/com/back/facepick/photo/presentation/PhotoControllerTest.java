package com.back.facepick.photo.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.facepick.global.authorization.resolver.AuthUserArgumentResolver;
import com.back.facepick.global.error.GlobalExceptionHandler;
import com.back.facepick.photo.application.PhotoCommandService;
import com.back.facepick.photo.application.dto.command.PhotoUploadCommand;
import com.back.facepick.photo.application.dto.result.PhotoCompleteResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult.FileResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult.Status;
import com.back.facepick.photo.domain.PhotoStatus;
import com.back.facepick.photo.domain.exception.PhotoFileMissingException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PhotoControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final String HASH = "a".repeat(64);

    @Mock
    private PhotoCommandService photoCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PhotoController(photoCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/albums/{albumId}/photos/uploads 는 200 과 파일별 결과")
    void createPhotoUploads() throws Exception {
        // given
        PhotoUploadCommand command =
                new PhotoUploadCommand(List.of(new PhotoUploadCommand.UploadFile(HASH, 1000L, "image/jpeg")));
        given(photoCommandService.createPhotoUploads(1L, 10L, command))
                .willReturn(new PhotoUploadResult(
                        NOW.plusMinutes(15),
                        List.of(new FileResult(
                                HASH, 12L, Status.UPLOAD_REQUIRED, "image/jpeg", "http://storage/upload"))));

        // when & then
        mockMvc.perform(post("/api/albums/10/photos/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filesJson(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].photoId").value(12))
                .andExpect(jsonPath("$.files[0].status").value("UPLOAD_REQUIRED"))
                .andExpect(jsonPath("$.files[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.files[0].uploadUrl").value("http://storage/upload"));
    }

    @Test
    @DisplayName("파일 목록이 비면 400 INVALID_INPUT")
    void rejectsEmptyFiles() throws Exception {
        // when & then
        mockMvc.perform(post("/api/albums/10/photos/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("업로드할 파일을 선택해주세요."));
    }

    @Test
    @DisplayName("101장이면 400")
    void rejectsTooManyFiles() throws Exception {
        // when & then
        mockMvc.perform(post("/api/albums/10/photos/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filesJson(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("한 번에 100장까지 올릴 수 있습니다."));
    }

    @Test
    @DisplayName("해시 형식이 틀리면 400")
    void rejectsInvalidHash() throws Exception {
        // when & then
        mockMvc.perform(
                        post("/api/albums/10/photos/uploads")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"files\":[{\"contentHash\":\"ABC\",\"byteSize\":1,\"contentType\":\"image/jpeg\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("파일 해시 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("POST /api/photos/{photoId}/complete 는 200 과 UPLOADED")
    void completePhoto() throws Exception {
        // given
        given(photoCommandService.completePhoto(1L, 12L))
                .willReturn(new PhotoCompleteResult(12L, PhotoStatus.UPLOADED));

        // when & then
        mockMvc.perform(post("/api/photos/12/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoId").value(12))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    @DisplayName("파일이 아직 없으면 409 PHOTO_FILE_MISSING")
    void completePhotoRejectsMissingFile() throws Exception {
        // given
        given(photoCommandService.completePhoto(1L, 12L)).willThrow(new PhotoFileMissingException());

        // when & then
        mockMvc.perform(post("/api/photos/12/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHOTO_FILE_MISSING"));
    }

    // 첫 파일은 HASH, 나머지는 서로 다른 64자 16진수 해시.
    private static String filesJson(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> String.format(
                        "{\"contentHash\":\"%s\",\"byteSize\":1000,\"contentType\":\"image/jpeg\"}",
                        i == 0 ? HASH : String.format("%064x", i)))
                .collect(Collectors.joining(",", "{\"files\":[", "]}"));
    }
}
