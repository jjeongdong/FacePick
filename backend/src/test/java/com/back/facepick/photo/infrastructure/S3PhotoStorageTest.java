package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

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
    private static S3Client s3Client;

    @BeforeAll
    static void setUp() {
        String endpoint = "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(S3_PORT);
        S3StorageConfig config = new S3StorageConfig();
        s3Client = config.s3Client(endpoint, REGION, ACCESS_KEY, SECRET_KEY);
        storage = new S3PhotoStorage(
                s3Client, config.s3Presigner(endpoint, REGION, ACCESS_KEY, SECRET_KEY), "photos", 15, 60);
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

    @Test
    @DisplayName("여러 파일을 지우고, 없는 키가 섞여도 성공한다")
    void deletesObjects() throws Exception {
        // given
        put(storage.createUploadUrl("albums/1/originals/del", "image/jpeg"), "image/jpeg", new byte[] {1});
        put(storage.createUploadUrl("albums/1/thumbnails/del.jpg", "image/jpeg"), "image/jpeg", new byte[] {2});

        // when
        storage.deleteObjects(
                List.of("albums/1/originals/del", "albums/1/thumbnails/del.jpg", "albums/1/previews/del.jpg"));

        // then
        assertThat(storage.findObjectSize("albums/1/originals/del")).isEmpty();
        assertThat(storage.findObjectSize("albums/1/thumbnails/del.jpg")).isEmpty();
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

    @Test
    @DisplayName("prefix 삭제 - 한 페이지(1000개)를 넘는 파일도 모두 지운다")
    void deletesMoreThanOnePage() {
        // given
        for (int i = 0; i < 1050; i++) {
            putDirect("albums/500/originals/" + i);
        }

        // when
        storage.deleteByPrefix("albums/500/");

        // then
        assertThat(countKeys("albums/500/")).isZero();
    }

    @Test
    @DisplayName("prefix 삭제 - ID 앞자리가 같은 다른 앨범(albums/7/ 과 albums/77/)의 파일은 남긴다")
    void keepsOtherAlbumWithSharedPrefix() {
        // given
        putDirect("albums/7/originals/a");
        putDirect("albums/7/thumbnails/a.jpg");
        putDirect("albums/77/originals/b");

        // when
        storage.deleteByPrefix("albums/7/");

        // then
        assertThat(countKeys("albums/7/")).isZero();
        assertThat(storage.findObjectSize("albums/77/originals/b")).isPresent();
    }

    @Test
    @DisplayName("prefix 삭제 - 파일이 없으면 아무 일 없이 끝난다")
    void deletesNothingForEmptyPrefix() {
        // when
        storage.deleteByPrefix("albums/501/");

        // then
        assertThat(countKeys("albums/501/")).isZero();
    }

    @Test
    @DisplayName("prefix 삭제 - '/' 로 끝나지 않으면 다른 앨범까지 지울 수 있어 거절한다")
    void rejectsPrefixWithoutSlash() {
        // when & then
        assertThatThrownBy(() -> storage.deleteByPrefix("albums/7")).isInstanceOf(IllegalArgumentException.class);
    }

    private static void putDirect(String key) {
        s3Client.putObject(request -> request.bucket("photos").key(key), RequestBody.fromBytes(new byte[] {1}));
    }

    private static int countKeys(String prefix) {
        return s3Client.listObjectsV2(request -> request.bucket("photos").prefix(prefix))
                .contents()
                .size();
    }
}
