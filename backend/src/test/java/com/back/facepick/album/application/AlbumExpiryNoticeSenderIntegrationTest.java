package com.back.facepick.album.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumExpiryNotice;
import com.back.facepick.album.domain.AlbumExpiryNoticeStatus;
import com.back.facepick.album.domain.ExpiryMail;
import com.back.facepick.album.domain.ExpiryMailSender;
import com.back.facepick.album.domain.MailSendOutcome;
import com.back.facepick.album.infrastructure.AlbumExpiryNoticeJpaRepository;
import com.back.facepick.album.infrastructure.AlbumExpiryNoticeRepositoryImpl;
import com.back.facepick.album.infrastructure.AlbumJpaRepository;
import com.back.facepick.album.infrastructure.AlbumRepositoryImpl;
import com.back.facepick.auth.application.AuthQueryApi;
import com.back.facepick.auth.fixture.CredentialFixture;
import com.back.facepick.auth.infrastructure.CredentialJpaRepository;
import com.back.facepick.auth.infrastructure.CredentialRepositoryImpl;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// 단위 테스트(AlbumExpiryNoticeSenderTest)는 가짜 트랜잭션 위에서 흐름만 본다.
// 여기서는 실제 트랜잭션으로 선점이 발송 전에 커밋되는지, 외부 호출이 트랜잭션 밖인지, 결과가 DB 에 남는지 본다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
    JpaAuditingConfig.class,
    AlbumExpiryNoticeRepositoryImpl.class,
    AlbumRepositoryImpl.class,
    CredentialRepositoryImpl.class,
    AuthQueryApi.class,
    AlbumExpiryNoticeSender.class,
    AlbumExpiryNoticeSenderIntegrationTest.FakeMailConfig.class
})
@TestPropertySource(properties = "facepick.album.expiry-notice.send-pause-millis=0")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class AlbumExpiryNoticeSenderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AlbumExpiryNoticeSender sender;

    @Autowired
    private FakeMailSender fakeMailSender;

    @Autowired
    private AlbumExpiryNoticeRepositoryImpl noticeRepository;

    @Autowired
    private AlbumExpiryNoticeJpaRepository noticeJpaRepository;

    @Autowired
    private AlbumRepositoryImpl albumRepository;

    @Autowired
    private AlbumJpaRepository albumJpaRepository;

    @Autowired
    private CredentialRepositoryImpl credentialRepository;

    @Autowired
    private CredentialJpaRepository credentialJpaRepository;

    @AfterEach
    void cleanUp() {
        fakeMailSender.available(true);
        fakeMailSender.respond(mail -> MailSendOutcome.sent("re_default"));
        noticeJpaRepository.deleteAll();
        albumJpaRepository.deleteAll();
        credentialJpaRepository.deleteAll();
    }

    // 만료 5일 전 앨범, 이메일 있는 멤버, 보낼 때가 된 알림 한 건
    private Long givenDueNotice() {
        LocalDateTime now = LocalDateTime.now();
        Album album = albumRepository.save(Album.create(
                1L, "제주 여행", now.minusDays(25), UUID.randomUUID().toString().substring(0, 22)));
        credentialRepository.save(CredentialFixture.credential(1L, "me@example.com"));
        return noticeRepository
                .save(AlbumExpiryNotice.create(album.getId(), 1L, now.minusMinutes(1)))
                .getId();
    }

    // 같은 앨범에 이메일 있는 멤버 여러 명과 각자의 알림
    private List<Long> givenDueNotices(int count) {
        LocalDateTime now = LocalDateTime.now();
        Album album = albumRepository.save(Album.create(
                1L, "제주 여행", now.minusDays(25), UUID.randomUUID().toString().substring(0, 22)));
        List<Long> ids = new ArrayList<>();
        for (long userId = 1; userId <= count; userId++) {
            credentialRepository.save(CredentialFixture.credential(userId, "member" + userId + "@example.com"));
            ids.add(noticeRepository
                    .save(AlbumExpiryNotice.create(album.getId(), userId, now.minusMinutes(1)))
                    .getId());
        }
        return ids;
    }

    private AlbumExpiryNotice reload(Long noticeId) {
        return noticeJpaRepository.findById(noticeId).orElseThrow();
    }

    @Test
    @DisplayName("성공 - 메일은 트랜잭션 밖에서 보내고, SENT 와 시도 횟수가 DB 에 남는다")
    void sendsOutsideTransactionAndPersistsSent() {
        // given
        Long noticeId = givenDueNotice();
        fakeMailSender.respond(mail -> MailSendOutcome.sent("re_1"));

        // when
        sender.send();

        // then
        assertThat(fakeMailSender.transactionActiveDuringSend).isFalse();
        AlbumExpiryNotice saved = reload(noticeId);
        assertThat(saved.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.SENT);
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getProviderMessageId()).isEqualTo("re_1");
    }

    @Test
    @DisplayName("발송 중 - 선점(시도 횟수·임대)은 메일을 보내기 전에 이미 커밋돼 있다")
    void claimIsCommittedBeforeSending() {
        // given
        Long noticeId = givenDueNotice();
        LocalDateTime before = LocalDateTime.now();
        fakeMailSender.respond(mail -> {
            // 발송 중에 다른 트랜잭션(다른 서버)이 보는 행 상태
            fakeMailSender.seenDuringSend = reload(noticeId);
            return MailSendOutcome.sent("re_1");
        });

        // when
        sender.send();

        // then
        AlbumExpiryNotice seen = fakeMailSender.seenDuringSend;
        assertThat(seen.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.PENDING);
        assertThat(seen.getAttempts()).isEqualTo(1);
        assertThat(seen.getNextAttemptAt()).isAfterOrEqualTo(before.plusMinutes(5));
    }

    @Test
    @DisplayName("일시 실패 - PENDING 으로 남고 다음 시도 시각이 DB 에 반영된다")
    void persistsRetryBackoff() {
        // given
        Long noticeId = givenDueNotice();
        LocalDateTime before = LocalDateTime.now();
        fakeMailSender.respond(mail -> MailSendOutcome.retryable("503"));

        // when
        sender.send();

        // then
        AlbumExpiryNotice saved = reload(noticeId);
        assertThat(saved.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.PENDING);
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getNextAttemptAt())
                .isAfterOrEqualTo(before.plusMinutes(1))
                .isBefore(before.plusMinutes(5));
        assertThat(saved.getLastError()).isEqualTo("503");
    }

    @Test
    @DisplayName("서킷 OPEN - 선점하지 않아 행이 그대로 남는다")
    void skipsRunWhenUnavailable() {
        // given
        Long noticeId = givenDueNotice();
        AlbumExpiryNotice before = reload(noticeId);
        fakeMailSender.available(false);

        // when
        sender.send();

        // then
        AlbumExpiryNotice after = reload(noticeId);
        assertThat(after.getAttempts()).isZero();
        assertThat(after.getNextAttemptAt()).isEqualTo(before.getNextAttemptAt());
    }

    @Test
    @DisplayName("배치 도중 서킷이 열리면 보낸 행은 SENT, 보내지 않은 행은 시도 횟수 0 으로 retryAt 에 남는다")
    void mixedBatchKeepsAttemptsForUnattempted() {
        // given
        List<Long> ids = givenDueNotices(3);
        LocalDateTime retryAt = LocalDateTime.now().plusSeconds(60).withNano(0);
        AtomicInteger calls = new AtomicInteger();
        fakeMailSender.respond(mail ->
                calls.incrementAndGet() == 1 ? MailSendOutcome.sent("re_1") : MailSendOutcome.notAttempted(retryAt));

        // when
        sender.send();

        // then
        List<AlbumExpiryNotice> saved = ids.stream().map(this::reload).toList();
        assertThat(saved)
                .filteredOn(n -> n.getStatus() == AlbumExpiryNoticeStatus.SENT)
                .hasSize(1)
                .allSatisfy(n -> assertThat(n.getAttempts()).isEqualTo(1));
        assertThat(saved)
                .filteredOn(n -> n.getStatus() == AlbumExpiryNoticeStatus.PENDING)
                .hasSize(2)
                .allSatisfy(n -> {
                    assertThat(n.getAttempts()).isZero();
                    assertThat(n.getNextAttemptAt()).isEqualTo(retryAt);
                });
    }

    static class FakeMailSender implements ExpiryMailSender {
        private Function<ExpiryMail, MailSendOutcome> behavior = mail -> MailSendOutcome.sent("re_default");
        private boolean available = true;
        private boolean transactionActiveDuringSend;
        private AlbumExpiryNotice seenDuringSend;

        void respond(Function<ExpiryMail, MailSendOutcome> behavior) {
            this.behavior = behavior;
        }

        void available(boolean available) {
            this.available = available;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public MailSendOutcome send(ExpiryMail mail) {
            transactionActiveDuringSend = TransactionSynchronizationManager.isActualTransactionActive();
            return behavior.apply(mail);
        }
    }

    @TestConfiguration
    static class FakeMailConfig {
        @Bean
        FakeMailSender fakeMailSender() {
            return new FakeMailSender();
        }
    }
}
