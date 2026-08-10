package com.liveklass.enrollment.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record EnrollmentRequest(
        @NotNull(message = "강의 id는 필수입니다.")
        Long courseId
) {
}
