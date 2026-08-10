package com.liveklass.enrollment.enrollment;

import java.util.Set;

/**
 * PENDING(신청완료, 결제대기) -> CONFIRMED(결제완료, 수강확정) -> CANCELLED(취소됨)
 * PENDING 상태에서 바로 CANCELLED로 갈 수도 있다(결제 전 취소).
 */
public enum EnrollmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    /** 정원 계산 시 자리를 차지하는 것으로 간주하는 상태 (결제 대기 중에도 자리는 선점됨) */
    public static final Set<EnrollmentStatus> SEAT_HOLDING = Set.of(PENDING, CONFIRMED);

    public boolean canTransitionTo(EnrollmentStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED -> false;
        };
    }
}
