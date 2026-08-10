package com.liveklass.enrollment.common.exception;

import org.springframework.http.HttpStatus;

public class CapacityExceededException extends BusinessException {

    public CapacityExceededException(Long courseId) {
        super(HttpStatus.CONFLICT, "정원이 마감되어 신청할 수 없습니다. courseId=" + courseId);
    }
}
