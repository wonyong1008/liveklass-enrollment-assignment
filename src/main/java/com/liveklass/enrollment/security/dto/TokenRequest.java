package com.liveklass.enrollment.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(
        @NotBlank(message = "userId는 필수입니다.")
        @Size(max = 64, message = "userId는 64자를 넘을 수 없습니다.")
        String userId
) {
}
