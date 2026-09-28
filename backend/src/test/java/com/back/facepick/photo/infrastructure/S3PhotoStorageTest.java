package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers(disabledWithoutDocker = true)
class S3PhotoStorageTest {

    private static final int S3_PORT = 9000;
    private static final String REGION = "us-east-1";
    private static final String ACCESS_KEY = "facepick";
    private static final String SECRET_KEY = "facepick-secret";

    // infra/docker-compose.yml 과 같은 이미지·설정으로 띄워 서명 방식이 실제 스토리지와 맞는지 본다.
    @Container
    static GenericContainer<?> seaweedfs = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:latest"))
            .withCopyFileToContainer(MountableFile.forClasspathResource("seaweedfs-s3.json"), "/etc/seaweedfs/s3.json")
            .withCommand(
                    "server",
                    "-dir=/data",
                    "-s3",
                    "-s3.port=" + S3_PORT,
                    "-s3.config=/etc/seaweedfs/s3.json",
                    "-master.volumeSizeLimitMB=64")
            .withExposedPorts(S3_PORT)
            .waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCodeMatching(code -> code < 500))
            .withStartupTimeout(Duration.ofMinutes(2));

    private static S3PhotoStorage storage;

    @BeforeAll
    static void setUp() {
        String endpoint = "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(S3_PORT);
        S3StorageConfig config = new S3StorageConfig();
        storage = new S3PhotoStorage(
                config.s3Client(endpoint, REGION, ACCESS_KEY, SECRET_KEY),
                config.s3Presigner(endpoint, REGION, ACCESS_KEY, SECRET_KEY),
                "photos",
                15,
                60);
        storage.createBucketIfMissing();
    }

    @Test
    @DisplayName("서명 URL 로 PUT 한 파일의 크기를 조회한다")
    void uploadsThroughPresignedUrl() throws Exception {
        // given
        byte[] content = "원본 사진 바이트".getBytes(StandardCharsets.UTF_8);
        URL url = storage.createUploadUrl("albums/1/originals/uploaded", "image/jpeg");

        // when
        HttpResponse<String> response = put(url, "image/jpeg", content);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(storage.findObjectSize("albums/1/originals/uploaded")).contains((long) content.length);
    }

    @Test
    @DisplayName("서명 때와 다른 Content-Type 으로 PUT 하면 스토리지가 거절한다")
    void rejectsDifferentContentType() throws Exception {
        // given
        URL url = storage.createUploadUrl("albums/1/originals/wrong-type", "image/jpeg");

        // when
        HttpResponse<String> response = put(url, "image/png", new byte[] {1, 2, 3});

        // then
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(storage.findObjectSize("albums/1/originals/wrong-type")).isEmpty();
    }

    @Test
    @DisplayName("없는 키면 빈 값")
    void returnsEmptyForMissingKey() {
        // when & then
        assertThat(storage.findObjectSize("albums/1/originals/missing")).isEmpty();
    }

    @Test
    @DisplayName("서명 URL 만료 시간은 설정값(분)이다")
    void exposesExpiry() {
        // when & then
        assertThat(storage.uploadUrlExpiry()).isEqualTo(Duration.ofMinutes(15));
    }

    private static HttpResponse<String> put(URL url, String contentType, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(url.toURI())
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("서명 GET URL 로 올린 파일을 그대로 받는다")
    void downloadsThroughPresignedUrl() throws Exception {
        // given
        byte[] content = "썸네일 바이트".getBytes(StandardCharsets.UTF_8);
        put(storage.createUploadUrl("albums/1/thumbnails/a.jpg", "image/jpeg"), "image/jpeg", content);

        // when
        HttpResponse<byte[]> response = get(storage.createDownloadUrl("albums/1/thumbnails/a.jpg"));

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
    }

    @Test
    @DisplayName("파일명을 주면 첨부 다운로드 헤더가 붙는다")
    void addsAttachmentHeader() throws Exception {
        // given
        put(storage.createUploadUrl("albums/1/originals/b", "image/jpeg"), "image/jpeg", new byte[] {1, 2, 3});

        // when
        HttpResponse<byte[]> response = get(storage.createDownloadUrl("albums/1/originals/b", "facepick-1.jpg"));

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Disposition"))
                .contains("attachment; filename=\"facepick-1.jpg\"");
    }

    @Test
    @DisplayName("다운로드 URL 만료 시간은 업로드와 따로 설정한다")
    void exposesDownloadExpiry() {
        // when & then
        assertThat(storage.downloadUrlExpiry()).isEqualTo(Duration.ofMinutes(60));
    }

    private static HttpResponse<byte[]> get(URL url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(url.toURI()).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
}
