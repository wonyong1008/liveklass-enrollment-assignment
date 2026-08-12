package com.liveklass.enrollment.course.dto;

import com.liveklass.enrollment.course.Course;
import com.liveklass.enrollment.course.CourseStatus;

import java.time.LocalDate;

public record CourseDetailResponse(
        Long id,
        String creatorId,
        String title,
        String description,
        Long price,
        int capacity,
        int enrolledCount,
        int remainingSeats,
        int waitlistCount,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status
) {
    public static CourseDetailResponse of(Course course, int enrolledCount, int waitlistCount) {
        return new CourseDetailResponse(
                course.getId(),
                course.getCreatorId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getCapacity(),
                enrolledCount,
                Math.max(course.getCapacity() - enrolledCount, 0),
                waitlistCount,
                course.getStartDate(),
                course.getEndDate(),
                course.getStatus()
        );
    }
}
