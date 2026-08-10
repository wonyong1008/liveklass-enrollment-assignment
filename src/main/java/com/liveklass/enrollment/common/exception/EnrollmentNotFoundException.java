package com.liveklass.enrollment.common.exception;

import org.springframework.http.HttpStatus;

public class EnrollmentNotFoundException extends BusinessException {

    public EnrollmentNotFoundException(Long enrollmentId) {
        super(HttpStatus.NOT_FOUND, "수강 신청 내역을 찾을 수 없습니다. id=" + enrollmentId);
    }
}
