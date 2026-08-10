package com.liveklass.enrollment.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidCourseStateException extends BusinessException {

    public InvalidCourseStateException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
