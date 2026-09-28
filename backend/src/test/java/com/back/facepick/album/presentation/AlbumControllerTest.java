package com.back.facepick.album.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.facepick.album.application.AlbumCommandService;
import com.back.facepick.album.application.AlbumQueryService;
import com.back.facepick.album.application.dto.command.AlbumCreateCommand;
import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.application.dto.result.AlbumResult;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.global.authorization.resolver.AuthUserArgumentResolver;
import com.back.facepick.global.error.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
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
class AlbumControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Mock
    private AlbumCommandService albumCommandService;

    @Mock
    private AlbumQueryService albumQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AlbumController(albumCommandService, albumQueryService))
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
    @DisplayName("POST /api/albums 는 201 과 만든 앨범")
    void createAlbum() throws Exception {
        // given
        given(albumCommandService.createAlbum(1L, new AlbumCreateCommand("제주 여행")))
                .willReturn(new AlbumCreateResult(10L, "제주 여행", "jeju-invite-code", NOW.plusDays(30), NOW));

        // when & then
        mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제주 여행\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.albumId").value(10))
                .andExpect(jsonPath("$.title").value("제주 여행"))
                .andExpect(jsonPath("$.inviteCode").value("jeju-invite-code"));
    }

    @Test
    @DisplayName("빈 제목이면 400 INVALID_INPUT")
    void createAlbumRejectsBlankTitle() throws Exception {
        // when & then
        mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("앨범 제목을 입력해주세요."));
    }

    @Test
    @DisplayName("GET /api/albums/me 는 200 과 내 앨범 목록")
    void getMyAlbums() throws Exception {
        // given
        given(albumQueryService.getMyAlbums(1L))
                .willReturn(List.of(new AlbumResult(10L, "제주 여행", AlbumRole.OWNER, NOW.plusDays(30), NOW)));

        // when & then
        mockMvc.perform(get("/api/albums/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].albumId").value(10))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }
}
