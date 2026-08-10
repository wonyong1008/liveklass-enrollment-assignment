package com.liveklass.enrollment.common.exception;

import org.springframework.http.HttpStatus;

public class WaitlistNotAllowedException extends BusinessException {

    public WaitlistNotAllowedException(Long courseId) {
        super(HttpStatus.CONFLICT, "정원이 남아있어 대기 신청이 필요하지 않습니다. 바로 수강 신청해주세요. courseId=" + courseId);
    }
}
