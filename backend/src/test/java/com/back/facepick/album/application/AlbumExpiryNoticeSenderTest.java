package com.back.facepick.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumExpiryNotice;
import com.back.facepick.album.domain.AlbumExpiryNoticeRepository;
import com.back.facepick.album.domain.AlbumExpiryNoticeStatus;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.ExpiryMail;
import com.back.facepick.album.domain.ExpiryMailSender;
import com.back.facepick.album.domain.MailSendOutcome;
import com.back.facepick.album.fixture.AlbumExpiryNoticeFixture;
import com.back.facepick.album.fixture.AlbumFixture;
import com.back.facepick.auth.application.AuthQueryApi;
import com.back.facepick.auth.application.dto.api.CredentialInfo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

class AlbumExpiryNoticeSenderTest {

    private static final Long ALBUM_ID = 10L;
    private static final Long USER_ID = 1L;

    private AlbumExpiryNoticeRepository noticeRepository;
    private AlbumRepository albumRepository;
    private AuthQueryApi authQueryApi;
    private ExpiryMailSender mailSender;
    private AlbumExpiryNoticeSender sender;

    @BeforeEach
    void setUp() {
        noticeRepository = mock(AlbumExpiryNoticeRepository.class);
        albumRepository = mock(AlbumRepository.class);
        authQueryApi = mock(AuthQueryApi.class);
        mailSender = mock(ExpiryMailSender.class);
        // 트랜잭션 경계는 통합 테스트(LockTest)가 본다. 여기서는 흐름과 상태 전이만 본다.
        sender = new AlbumExpiryNoticeSender(
                noticeRepository,
                albumRepository,
                authQueryApi,
                mailSender,
                mock(PlatformTransactionManager.class),
                20,
                5,
                0);
    }

    // 만료까지 5일 남은 앨범 (생성 25일 전)
    private static Album liveAlbum() {
        return AlbumFixture.album(ALBUM_ID, USER_ID, LocalDateTime.now().minusDays(25));
    }

    private AlbumExpiryNotice givenDue(Album album, String email) {
        AlbumExpiryNotice notice = AlbumExpiryNoticeFixture.pending(42L, ALBUM_ID, USER_ID);
        given(noticeRepository.findDueForUpdate(any(), anyInt())).willReturn(List.of(notice));
        given(noticeRepository.findById(42L)).willReturn(Optional.of(notice));
        given(albumRepository.findAllByIds(List.of(ALBUM_ID))).willReturn(album == null ? List.of() : List.of(album));
        given(authQueryApi.getInfos(List.of(USER_ID)))
                .willReturn(email == null ? Map.of() : Map.of(USER_ID, new CredentialInfo(USER_ID, email)));
        return notice;
    }

    @Test
    @DisplayName("보낼 알림이 없으면 앨범·이메일을 조회하지 않는다")
    void doesNothingWhenNothingDue() {
        // given
        given(noticeRepository.findDueForUpdate(any(), anyInt())).willReturn(List.of());

        // when
        sender.send();

        // then
        then(albumRepository).shouldHaveNoInteractions();
        then(authQueryApi).shouldHaveNoInteractions();
        then(mailSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("성공 - 멱등 키·주소·앨범 정보로 보내고 SENT 로 저장한다")
    void marksSent() {
        // given
        Album album = liveAlbum();
        AlbumExpiryNotice notice = givenDue(album, "me@example.com");
        given(mailSender.send(any())).willReturn(MailSendOutcome.sent("re_abc"));

        // when
        sender.send();

        // then
        ArgumentCaptor<ExpiryMail> mail = ArgumentCaptor.forClass(ExpiryMail.class);
        then(mailSender).should().send(mail.capture());
        assertThat(mail.getValue())
                .isEqualTo(new ExpiryMail(
                        notice.idempotencyKey(), "me@example.com", ALBUM_ID, "제주 여행", album.getExpiresAt()));
        assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.SENT);
        assertThat(notice.getProviderMessageId()).isEqualTo("re_abc");
        assertThat(notice.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("일시 실패 - PENDING 으로 두고 다음 시도를 미룬다")
    void retriesLater() {
        // given
        AlbumExpiryNotice notice = givenDue(liveAlbum(), "me@example.com");
        given(mailSender.send(any())).willReturn(MailSendOutcome.retryable("503"));
        LocalDateTime before = LocalDateTime.now();

        // when
        sender.send();

        // then
        assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.PENDING);
        assertThat(notice.getNextAttemptAt()).isAfterOrEqualTo(before.plusMinutes(1));
        assertThat(notice.getLastError()).isEqualTo("503");
    }

    @Test
    @DisplayName("영구 실패 - FAILED")
    void marksFailed() {
        // given
        AlbumExpiryNotice notice = givenDue(liveAlbum(), "me@example.com");
        given(mailSender.send(any())).willReturn(MailSendOutcome.permanent("422"));

        // when
        sender.send();

        // then
        assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.FAILED);
    }

    @Test
    @DisplayName("메일 구현이 예외를 던지면 일시 실패로 다룬다")
    void treatsThrownSenderAsRetryable() {
        // given
        AlbumExpiryNotice notice = givenDue(liveAlbum(), "me@example.com");
        given(mailSender.send(any())).willThrow(new IllegalStateException("boom"));

        // when
        sender.send();

        // then
        assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.PENDING);
        assertThat(notice.getLastError()).contains("boom");
    }

    @Test
    @DisplayName("앨범이 사라졌으면 보내지 않고 SKIPPED")
    void skipsMissingAlbum() {
        // given
        AlbumExpiryNotice notice = givenDue(null, "me@example.com");

        // when
        sender.send();

        // then
        then(mailSender).should(never()).send(any());
        assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.SKIPPED);
    }

    @Test
    @DisplayName("스캔 뒤 앨범이 이미 만료됐으면 보내지 않고 SKIPPED")
    void skipsExpiredAlbum() {
        // given
        Album expired =
                AlbumFixture.album(ALBUM_ID, USER_ID, LocalDateTime.now().minusDays(31));
        AlbumExpiryNotice notice = givenDue(expired, "me@example.com");

        // when
        sender.send();

        // then
        then(mailSender).should(never()).send(any());
        assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.SKIPPED);
    }

    @Test
    @DisplayName("이메일이 없으면 보내지 않고 SKIPPED")
    void skipsWithoutEmail() {
        // given
        AlbumExpiryNotice notice = givenDue(liveAlbum(), null);

        // when
        sender.send();

        // then
        then(mailSender).should(never()).send(any());
        assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.SKIPPED);
    }

    @Test
    @DisplayName("한 행의 결과 저장이 실패해도 다음 행은 보낸다")
    void continuesAfterRowFailure() {
        // given
        Album album = liveAlbum();
        AlbumExpiryNotice first = AlbumExpiryNoticeFixture.pending(41L, ALBUM_ID, USER_ID);
        AlbumExpiryNotice second = AlbumExpiryNoticeFixture.pending(42L, ALBUM_ID, 2L);
        given(noticeRepository.findDueForUpdate(any(), anyInt())).willReturn(List.of(first, second));
        willThrow(new IllegalStateException("db down")).given(noticeRepository).findById(41L);
        given(noticeRepository.findById(42L)).willReturn(Optional.of(second));
        given(albumRepository.findAllByIds(List.of(ALBUM_ID))).willReturn(List.of(album));
        given(authQueryApi.getInfos(List.of(USER_ID, 2L)))
                .willReturn(Map.of(
                        USER_ID,
                        new CredentialInfo(USER_ID, "a@example.com"),
                        2L,
                        new CredentialInfo(2L, "b@example.com")));
        given(mailSender.send(any())).willReturn(MailSendOutcome.sent("re_x"));

        // when
        sender.send();

        // then
        then(mailSender).should(times(2)).send(any());
        assertThat(second.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.SENT);
    }

    @Test
    @DisplayName("메일을 보낼 때마다 설정한 만큼 쉰다 (Resend 요청 한도 429 로 시도 횟수를 낭비하지 않게)")
    void pausesBetweenSends() {
        // given
        AlbumExpiryNoticeSender pacedSender = new AlbumExpiryNoticeSender(
                noticeRepository,
                albumRepository,
                authQueryApi,
                mailSender,
                mock(PlatformTransactionManager.class),
                20,
                5,
                100);
        Album album = liveAlbum();
        List<AlbumExpiryNotice> notices = List.of(
                AlbumExpiryNoticeFixture.pending(41L, ALBUM_ID, USER_ID),
                AlbumExpiryNoticeFixture.pending(42L, ALBUM_ID, 2L),
                AlbumExpiryNoticeFixture.pending(43L, ALBUM_ID, 3L));
        given(noticeRepository.findDueForUpdate(any(), anyInt())).willReturn(notices);
        notices.forEach(n -> given(noticeRepository.findById(n.getId())).willReturn(Optional.of(n)));
        given(albumRepository.findAllByIds(List.of(ALBUM_ID))).willReturn(List.of(album));
        given(authQueryApi.getInfos(List.of(USER_ID, 2L, 3L)))
                .willReturn(Map.of(
                        USER_ID,
                        new CredentialInfo(USER_ID, "a@example.com"),
                        2L,
                        new CredentialInfo(2L, "b@example.com"),
                        3L,
                        new CredentialInfo(3L, "c@example.com")));
        given(mailSender.send(any())).willReturn(MailSendOutcome.sent("re_x"));
        long started = System.nanoTime();

        // when
        pacedSender.send();

        // then
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(200));
        assertThat(notices).allMatch(n -> n.getStatus() == AlbumExpiryNoticeStatus.SENT);
    }
}
