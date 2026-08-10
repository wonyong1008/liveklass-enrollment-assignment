package com.liveklass.enrollment.course;

import com.liveklass.enrollment.common.BaseTimeEntity;
import com.liveklass.enrollment.common.exception.InvalidCourseStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "course")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false, length = 64)
    private String creatorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status;

    public Course(String creatorId, String title, String description, BigDecimal price,
                  int capacity, LocalDate startDate, LocalDate endDate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("정원은 1명 이상이어야 합니다.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }
        this.creatorId = creatorId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.capacity = capacity;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = CourseStatus.DRAFT;
    }

    public void open() {
        transitionTo(CourseStatus.OPEN);
    }

    public void close() {
        transitionTo(CourseStatus.CLOSED);
    }

    private void transitionTo(CourseStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidCourseStateException(
                    "강의 상태를 %s에서 %s(으)로 변경할 수 없습니다.".formatted(this.status, target));
        }
        this.status = target;
    }

    public boolean isOpenForEnrollment() {
        return this.status == CourseStatus.OPEN;
    }

    public boolean isOwnedBy(String userId) {
        return this.creatorId.equals(userId);
    }
}
