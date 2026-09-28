package com.back.facepick.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.auth.domain.exception.AuthInvalidCredentialsException;
import com.back.facepick.auth.domain.exception.AuthInvalidEmailException;
import com.back.facepick.auth.domain.exception.AuthInvalidPasswordException;
import com.back.facepick.auth.fixture.FakePasswordEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CredentialTest {

    private final PasswordEncryptor encryptor = new FakePasswordEncryptor();

    @Nested
    @DisplayName("자격 증명 생성")
    class Create {

        @Test
        @DisplayName("이메일은 공백을 지우고 소문자로, 비밀번호는 해시로 저장한다")
        void normalizesEmailAndHashesPassword() {
            // when
            Credential credential = Credential.create(1L, "  Me@Example.COM ", "password123", encryptor);

            // then
            assertThat(credential.getEmail()).isEqualTo("me@example.com");
            assertThat(credential.getPasswordHash()).isEqualTo("hashed:password123");
            assertThat(credential.getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("빈 이메일은 허용하지 않는다")
        void throwsWhenEmailBlank() {
            // when & then
            assertThatThrownBy(() -> Credential.create(1L, "  ", "password123", encryptor))
                    .isInstanceOf(AuthInvalidEmailException.class);
        }

        @Test
        @DisplayName("8자보다 짧은 비밀번호는 허용하지 않는다")
        void throwsWhenPasswordTooShort() {
            // when & then
            assertThatThrownBy(() -> Credential.create(1L, "me@example.com", "short", encryptor))
                    .isInstanceOf(AuthInvalidPasswordException.class);
        }

        @Test
        @DisplayName("72바이트를 넘는 비밀번호는 글자 수가 적어도 허용하지 않는다")
        void throwsWhenPasswordOver72Bytes() {
            // given
            String korean25 = "가".repeat(25); // UTF-8 로 75바이트

            // when & then
            assertThatThrownBy(() -> Credential.create(1L, "me@example.com", korean25, encryptor))
                    .isInstanceOf(AuthInvalidPasswordException.class);
        }

        @Test
        @DisplayName("정확히 72바이트인 비밀번호는 허용한다")
        void acceptsPasswordOf72Bytes() {
            // when & then
            assertThatCode(() -> Credential.create(1L, "me@example.com", "a".repeat(72), encryptor))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("비밀번호 확인")
    class Authenticate {

        @Test
        @DisplayName("비밀번호가 맞으면 통과한다")
        void passesWhenMatches() {
            // given
            Credential credential = Credential.create(1L, "me@example.com", "password123", encryptor);

            // when & then
            assertThatCode(() -> credential.authenticate("password123", encryptor))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("비밀번호가 틀리면 AuthInvalidCredentialsException 을 던진다")
        void throwsWhenMismatch() {
            // given
            Credential credential = Credential.create(1L, "me@example.com", "password123", encryptor);

            // when & then
            assertThatThrownBy(() -> credential.authenticate("wrong-password", encryptor))
                    .isInstanceOf(AuthInvalidCredentialsException.class);
        }
    }
}
