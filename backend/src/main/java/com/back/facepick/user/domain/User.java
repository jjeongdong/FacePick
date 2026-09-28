package com.back.facepick.user.domain;

import com.back.facepick.global.persistence.BaseTimeEntity;
import com.back.facepick.user.domain.exception.UserInvalidNicknameException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseTimeEntity {
    private static final int MAX_NICKNAME_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, length = MAX_NICKNAME_LENGTH)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    private User(String nickname, UserRole role) {
        this.nickname = nickname;
        this.role = role;
    }

    public static User create(String nickname) {
        if (nickname == null || nickname.isBlank() || nickname.strip().length() > MAX_NICKNAME_LENGTH) {
            throw new UserInvalidNicknameException();
        }
        return new User(nickname.strip(), UserRole.USER);
    }
}
