package com.liveklass.enrollment.common.exception;

import org.springframework.http.HttpStatus;

public class CancellationPeriodExpiredException extends BusinessException {

    public CancellationPeriodExpiredException(Long enrollmentId) {
        super(HttpStatus.CONFLICT, "취소 가능 기간이 지나 취소할 수 없습니다. enrollmentId=" + enrollmentId);
    }
}
