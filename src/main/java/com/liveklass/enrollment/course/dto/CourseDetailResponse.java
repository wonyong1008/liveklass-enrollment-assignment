package com.liveklass.enrollment.course.dto;

import com.liveklass.enrollment.course.Course;
import com.liveklass.enrollment.course.CourseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CourseDetailResponse(
        Long id,
        String creatorId,
        String title,
        String description,
        BigDecimal price,
        int capacity,
        int enrolledCount,
        int remainingSeats,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status
) {
    public static CourseDetailResponse of(Course course, int enrolledCount) {
        return new CourseDetailResponse(
                course.getId(),
                course.getCreatorId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getCapacity(),
                enrolledCount,
                Math.max(course.getCapacity() - enrolledCount, 0),
                course.getStartDate(),
                course.getEndDate(),
                course.getStatus()
        );
    }
}
