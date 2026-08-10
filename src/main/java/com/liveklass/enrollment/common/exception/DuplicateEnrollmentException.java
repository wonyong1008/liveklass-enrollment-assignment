package com.liveklass.enrollment.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEnrollmentException extends BusinessException {

    public DuplicateEnrollmentException(Long courseId, String userId) {
        super(HttpStatus.CONFLICT, "이미 신청했거나 수강 중인 강의입니다. courseId=" + courseId + ", userId=" + userId);
    }
}
