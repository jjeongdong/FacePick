package com.back.facepick.photo.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.facepick.global.authorization.resolver.AuthUserArgumentResolver;
import com.back.facepick.global.error.GlobalExceptionHandler;
import com.back.facepick.global.response.CursorPageResult;
import com.back.facepick.photo.application.PhotoCommandService;
import com.back.facepick.photo.application.PhotoQueryService;
import com.back.facepick.photo.application.dto.command.PhotoDeleteCommand;
import com.back.facepick.photo.application.dto.command.PhotoUploadCommand;
import com.back.facepick.photo.application.dto.result.PhotoCompleteResult;
import com.back.facepick.photo.application.dto.result.PhotoDeleteResult;
import com.back.facepick.photo.application.dto.result.PhotoDetailResult;
import com.back.facepick.photo.application.dto.result.PhotoSummaryResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult.FileResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult.Status;
import com.back.facepick.photo.domain.PhotoStatus;
import com.back.facepick.photo.domain.exception.PhotoFileMissingException;
import com.back.facepick.photo.domain.exception.PhotoNotDeletableException;
import com.back.facepick.photo.domain.exception.PhotoViewNotAlbumMemberException;
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

    @Mock
    private PhotoQueryService photoQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PhotoController(photoCommandService, photoQueryService))
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

    @Test
    @DisplayName("GET /api/albums/{albumId}/photos 는 200 과 커서 페이지")
    void getPhotos() throws Exception {
        // given
        given(photoQueryService.getPhotos(1L, 10L, "abc", 2))
                .willReturn(new CursorPageResult<>(
                        List.of(new PhotoSummaryResult(
                                12L, "http://storage/thumb", 4032, 3024, NOW.minusDays(3), NOW, NOW.plusHours(1))),
                        "next",
                        true));

        // when & then
        mockMvc.perform(get("/api/albums/10/photos").param("cursor", "abc").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].photoId").value(12))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value("http://storage/thumb"))
                .andExpect(jsonPath("$.content[0].width").value(4032))
                .andExpect(jsonPath("$.nextCursor").value("next"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("size 를 빼면 20, 커서를 빼면 null 로 넘긴다")
    void getPhotosUsesDefaults() throws Exception {
        // given
        given(photoQueryService.getPhotos(1L, 10L, null, 20)).willReturn(CursorPageResult.empty());

        // when & then
        mockMvc.perform(get("/api/albums/10/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("size 가 1~100 밖이거나 숫자가 아니면 400 INVALID_INPUT, 서비스를 부르지 않는다")
    void rejectsInvalidSize() throws Exception {
        // when & then
        for (String size : List.of("0", "101", "abc")) {
            mockMvc.perform(get("/api/albums/10/photos").param("size", size))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        then(photoQueryService).should(never()).getPhotos(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("GET /api/photos/{photoId} 는 200 과 미리보기·원본 URL")
    void getPhoto() throws Exception {
        // given
        given(photoQueryService.getPhoto(1L, 12L))
                .willReturn(new PhotoDetailResult(
                        12L,
                        10L,
                        "image/heic",
                        3456789L,
                        4032,
                        3024,
                        NOW.minusDays(3),
                        NOW,
                        "http://storage/preview",
                        "http://storage/original",
                        NOW.plusHours(1)));

        // when & then
        mockMvc.perform(get("/api/photos/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albumId").value(10))
                .andExpect(jsonPath("$.contentType").value("image/heic"))
                .andExpect(jsonPath("$.previewUrl").value("http://storage/preview"))
                .andExpect(jsonPath("$.originalUrl").value("http://storage/original"));
    }

    @Test
    @DisplayName("참여자가 아니면 403 PHOTO_VIEW_NOT_ALBUM_MEMBER")
    void getPhotoRejectsNonMember() throws Exception {
        // given
        given(photoQueryService.getPhoto(1L, 12L)).willThrow(new PhotoViewNotAlbumMemberException());

        // when & then
        mockMvc.perform(get("/api/photos/12"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PHOTO_VIEW_NOT_ALBUM_MEMBER"));
    }

    @Test
    @DisplayName("POST /api/albums/{albumId}/photos/delete 는 200 과 지운 ID")
    void deletePhotos() throws Exception {
        // given
        given(photoCommandService.deletePhotos(1L, 10L, new PhotoDeleteCommand(List.of(12L, 13L))))
                .willReturn(new PhotoDeleteResult(List.of(12L, 13L)));

        // when & then
        mockMvc.perform(post("/api/albums/10/photos/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\":[12,13]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedPhotoIds[0]").value(12))
                .andExpect(jsonPath("$.deletedPhotoIds[1]").value(13));
    }

    @Test
    @DisplayName("권한 없는 사진이 섞이면 403 PHOTO_NOT_DELETABLE")
    void deletePhotosRejectsNotDeletable() throws Exception {
        // given
        given(photoCommandService.deletePhotos(1L, 10L, new PhotoDeleteCommand(List.of(12L))))
                .willThrow(new PhotoNotDeletableException());

        // when & then
        mockMvc.perform(post("/api/albums/10/photos/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\":[12]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PHOTO_NOT_DELETABLE"));
    }

    @Test
    @DisplayName("목록이 비었거나 없거나 null ID 가 있거나 100장을 넘으면 400 INVALID_INPUT, 서비스를 부르지 않는다")
    void deletePhotosRejectsInvalidBody() throws Exception {
        // given
        String tooMany = IntStream.rangeClosed(1, 101)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(",", "{\"photoIds\":[", "]}"));

        // when & then
        for (String body :
                List.of("{\"photoIds\":[]}", "{}", "{\"photoIds\":null}", "{\"photoIds\":[null]}", tooMany)) {
            mockMvc.perform(post("/api/albums/10/photos/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        then(photoCommandService).should(never()).deletePhotos(any(), any(), any());
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
