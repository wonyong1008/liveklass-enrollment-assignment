package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.common.BaseTimeEntity;
import com.liveklass.enrollment.common.exception.CancellationPeriodExpiredException;
import com.liveklass.enrollment.common.exception.InvalidEnrollmentStateException;
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

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "enrollment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public Enrollment(Long courseId, String userId, LocalDateTime appliedAt) {
        this.courseId = courseId;
        this.userId = userId;
        this.status = EnrollmentStatus.PENDING;
        this.appliedAt = appliedAt;
    }

    public boolean isOwnedBy(String userId) {
        return this.userId.equals(userId);
    }

    public void confirm(LocalDateTime now) {
        transitionTo(EnrollmentStatus.CONFIRMED);
        this.confirmedAt = now;
    }

    /**
     * PENDING 상태는 언제든 취소 가능(아직 결제 전). CONFIRMED 상태는 결제 확정일로부터
     * cancellableWindow 이내에만 취소 가능하다.
     */
    public void cancel(LocalDateTime now, Duration cancellableWindow) {
        if (this.status == EnrollmentStatus.CONFIRMED) {
            LocalDateTime deadline = this.confirmedAt.plus(cancellableWindow);
            if (now.isAfter(deadline)) {
                throw new CancellationPeriodExpiredException(this.id);
            }
        }
        transitionTo(EnrollmentStatus.CANCELLED);
        this.cancelledAt = now;
    }

    private void transitionTo(EnrollmentStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidEnrollmentStateException(
                    "수강 신청 상태를 %s에서 %s(으)로 변경할 수 없습니다.".formatted(this.status, target));
        }
        this.status = target;
    }
}
