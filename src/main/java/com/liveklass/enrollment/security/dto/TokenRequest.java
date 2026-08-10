package com.liveklass.enrollment.security.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId
) {
}
