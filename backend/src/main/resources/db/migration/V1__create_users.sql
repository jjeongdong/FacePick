CREATE TABLE users (
    user_id     BIGSERIAL    PRIMARY KEY,
    nickname    VARCHAR(20)  NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    modified_at TIMESTAMP(6) NOT NULL
);
