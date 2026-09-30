package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.back.facepick.photo.domain.exception.PhotoNotFoundException;
import com.back.facepick.photo.domain.exception.PhotoProcessingFailedException;
import com.back.facepick.photo.domain.exception.PhotoProcessingUnavailableException;
import com.back.facepick.photo.fixture.PhotoFixture;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SyncPhotoPipelineTest {

    private static final String PROCESSED =
            "{\"photoId\":12,\"albumId\":10,\"previewKey\":\"albums/10/previews/a.jpg\",\"width\":640,\"height\":480}";

    private MockRestServiceServer thumbnailServer;
    private MockRestServiceServer faceServer;
    private SyncPhotoPipeline pipeline;

    @BeforeEach
    void setUp() {
        RestClient.Builder thumbnailBuilder = RestClient.builder().baseUrl("http://thumbnail");
        thumbnailServer = MockRestServiceServer.bindTo(thumbnailBuilder).build();
        RestClient.Builder faceBuilder = RestClient.builder().baseUrl("http://face");
        faceServer = MockRestServiceServer.bindTo(faceBuilder).build();
        pipeline = new SyncPhotoPipeline(thumbnailBuilder.build(), faceBuilder.build());
    }

    private void thumbnailSucceeds() {
        thumbnailServer
                .expect(requestTo("http://thumbnail/process"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"photoId\":12}"))
                .andRespond(withSuccess(PROCESSED, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("thumbnail 응답의 미리보기 정보를 face 요청으로 넘긴다")
    void passesThumbnailResultToFace() {
        // given
        thumbnailSucceeds();
        faceServer
                .expect(requestTo("http://face/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content().json("{\"photoId\":12,\"albumId\":10,\"previewKey\":\"albums/10/previews/a.jpg\"}"))
                .andRespond(withSuccess(
                        "{\"photoId\":12,\"result\":\"ANALYZED\",\"faceCount\":2}", MediaType.APPLICATION_JSON));

        // when
        pipeline.afterCommit(12L);

        // then
        thumbnailServer.verify();
        faceServer.verify();
    }

    @Test
    @DisplayName("트랜잭션 안에서는 워커를 부르지 않는다")
    void doesNothingOnCompleted() {
        // when
        pipeline.onCompleted(PhotoFixture.uploaded(12L, 10L, 1L, PhotoFixture.HASH), PhotoFixture.NOW);

        // then
        thumbnailServer.verify();
        faceServer.verify();
    }

    @Nested
    @DisplayName("처리를 넘기지 못하면")
    class Failure {

        @Test
        @DisplayName("thumbnail 에 연결할 수 없으면 PhotoProcessingUnavailableException 이고 face 는 부르지 않는다")
        void thumbnailConnectionRefused() {
            // given
            thumbnailServer
                    .expect(requestTo("http://thumbnail/process"))
                    .andRespond(withException(new ConnectException("Connection refused")));

            // when & then
            assertThatThrownBy(() -> pipeline.afterCommit(12L)).isInstanceOf(PhotoProcessingUnavailableException.class);
            faceServer.verify();
        }

        @Test
        @DisplayName("thumbnail 이 500 이면 PhotoProcessingUnavailableException")
        void thumbnailServerError() {
            // given
            thumbnailServer.expect(requestTo("http://thumbnail/process")).andRespond(withServerError());

            // when & then
            assertThatThrownBy(() -> pipeline.afterCommit(12L)).isInstanceOf(PhotoProcessingUnavailableException.class);
        }

        @Test
        @DisplayName("워커가 400 이면 연동 문제라 PhotoProcessingUnavailableException")
        void thumbnailBadRequest() {
            // given
            thumbnailServer
                    .expect(requestTo("http://thumbnail/process"))
                    .andRespond(withStatus(HttpStatusCode.valueOf(400)));

            // when & then
            assertThatThrownBy(() -> pipeline.afterCommit(12L)).isInstanceOf(PhotoProcessingUnavailableException.class);
        }

        @Test
        @DisplayName("thumbnail 이 404 면 그사이 사진이 지워진 것이라 PhotoNotFoundException")
        void thumbnailNotFound() {
            // given
            thumbnailServer
                    .expect(requestTo("http://thumbnail/process"))
                    .andRespond(withStatus(HttpStatusCode.valueOf(404)));

            // when & then
            assertThatThrownBy(() -> pipeline.afterCommit(12L)).isInstanceOf(PhotoNotFoundException.class);
            faceServer.verify();
        }

        @Test
        @DisplayName("thumbnail 이 422 면 다시 해도 안 되는 사진이라 PhotoProcessingFailedException")
        void thumbnailUnprocessable() {
            // given
            thumbnailServer
                    .expect(requestTo("http://thumbnail/process"))
                    .andRespond(withStatus(HttpStatusCode.valueOf(422)));

            // when & then
            assertThatThrownBy(() -> pipeline.afterCommit(12L)).isInstanceOf(PhotoProcessingFailedException.class);
        }

        @Test
        @DisplayName("face 응답이 타임아웃되면 PhotoProcessingUnavailableException")
        void faceTimeout() {
            // given
            thumbnailSucceeds();
            faceServer
                    .expect(requestTo("http://face/analyze"))
                    .andRespond(withException(new SocketTimeoutException("Read timed out")));

            // when & then
            assertThatThrownBy(() -> pipeline.afterCommit(12L)).isInstanceOf(PhotoProcessingUnavailableException.class);
        }

        @Test
        @DisplayName("face 가 422 면 PhotoProcessingFailedException")
        void faceUnprocessable() {
            // given
            thumbnailSucceeds();
            faceServer.expect(requestTo("http://face/analyze")).andRespond(withStatus(HttpStatusCode.valueOf(422)));

            // when & then
            assertThatThrownBy(() -> pipeline.afterCommit(12L)).isInstanceOf(PhotoProcessingFailedException.class);
        }
    }
}
