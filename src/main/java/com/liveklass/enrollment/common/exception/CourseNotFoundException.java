package com.liveklass.enrollment.common.exception;

import org.springframework.http.HttpStatus;

public class CourseNotFoundException extends BusinessException {

    public CourseNotFoundException(Long courseId) {
        super(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다. id=" + courseId);
    }
}
