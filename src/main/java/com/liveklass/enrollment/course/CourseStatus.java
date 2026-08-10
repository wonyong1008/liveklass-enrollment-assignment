package com.liveklass.enrollment.course;

/**
 * DRAFT(초안) -> OPEN(모집중) -> CLOSED(모집마감) 순으로만 전이 가능하다.
 */
public enum CourseStatus {
    DRAFT,
    OPEN,
    CLOSED;

    public boolean canTransitionTo(CourseStatus target) {
        return switch (this) {
            case DRAFT -> target == OPEN;
            case OPEN -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}
