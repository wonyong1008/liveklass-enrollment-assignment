package com.liveklass.enrollment.course.dto;

import com.liveklass.enrollment.course.Course;
import com.liveklass.enrollment.course.CourseStatus;

import java.time.LocalDate;

public record CourseResponse(
        Long id,
        String creatorId,
        String title,
        Long price,
        int capacity,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status
) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getCreatorId(),
                course.getTitle(),
                course.getPrice(),
                course.getCapacity(),
                course.getStartDate(),
                course.getEndDate(),
                course.getStatus()
        );
    }
}
