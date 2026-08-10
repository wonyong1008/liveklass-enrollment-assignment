package com.liveklass.enrollment.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidEnrollmentStateException extends BusinessException {

    public InvalidEnrollmentStateException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
