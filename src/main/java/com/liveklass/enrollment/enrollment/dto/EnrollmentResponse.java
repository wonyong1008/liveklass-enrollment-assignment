package com.liveklass.enrollment.enrollment.dto;

import com.liveklass.enrollment.enrollment.Enrollment;
import com.liveklass.enrollment.enrollment.EnrollmentStatus;

import java.time.LocalDateTime;

public record EnrollmentResponse(
        Long id,
        Long courseId,
        String userId,
        EnrollmentStatus status,
        LocalDateTime appliedAt,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt
) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getCourseId(),
                enrollment.getUserId(),
                enrollment.getStatus(),
                enrollment.getAppliedAt(),
                enrollment.getConfirmedAt(),
                enrollment.getCancelledAt()
        );
    }
}
