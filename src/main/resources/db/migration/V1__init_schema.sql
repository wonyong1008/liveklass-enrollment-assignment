CREATE TABLE course (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    creator_id   VARCHAR(64)    NOT NULL,
    title        VARCHAR(200)   NOT NULL,
    description  TEXT,
    price        BIGINT         NOT NULL,
    capacity     INT            NOT NULL,
    start_date   DATE           NOT NULL,
    end_date     DATE           NOT NULL,
    status       VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    created_at   DATETIME       NOT NULL,
    updated_at   DATETIME       NOT NULL,

    CONSTRAINT chk_course_capacity CHECK (capacity > 0),
    CONSTRAINT chk_course_period CHECK (end_date >= start_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_course_creator ON course (creator_id);
CREATE INDEX idx_course_status ON course (status);

CREATE TABLE enrollment (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id     BIGINT      NOT NULL,
    user_id       VARCHAR(64) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    applied_at    DATETIME    NOT NULL,
    confirmed_at  DATETIME    NULL,
    cancelled_at  DATETIME    NULL,
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NOT NULL,

    CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 정원 계산(course_id + status)과 크리에이터별 수강생 조회에서 가장 자주 타는 인덱스
CREATE INDEX idx_enrollment_course_status ON enrollment (course_id, status);
CREATE INDEX idx_enrollment_user ON enrollment (user_id);
