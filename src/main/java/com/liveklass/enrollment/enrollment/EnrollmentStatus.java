package com.liveklass.enrollment.enrollment;

import java.util.Set;

/**
 * PENDING(신청완료, 결제대기) -> CONFIRMED(결제완료, 수강확정) -> CANCELLED(취소됨)
 * PENDING 상태에서 바로 CANCELLED로 갈 수도 있다(결제 전 취소).
 * WAITLISTED(대기중)는 정원이 가득 찼을 때만 생기며, 좌석이 비면 PENDING으로 자동 승급되거나
 * 대기 자체를 CANCELLED로 포기할 수 있다.
 */
public enum EnrollmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    WAITLISTED;

    /** 정원 계산 시 자리를 차지하는 것으로 간주하는 상태 (결제 대기 중에도 자리는 선점됨). WAITLISTED는 포함하지 않는다. */
    public static final Set<EnrollmentStatus> SEAT_HOLDING = Set.of(PENDING, CONFIRMED);

    /** 한 사용자가 한 강의에 대해 동시에 하나만 가질 수 있는 "살아있는" 상태 (중복 신청/중복 대기 방지용) */
    public static final Set<EnrollmentStatus> ACTIVE = Set.of(PENDING, CONFIRMED, WAITLISTED);

    public boolean canTransitionTo(EnrollmentStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == CANCELLED;
            case WAITLISTED -> target == PENDING || target == CANCELLED;
            case CANCELLED -> false;
        };
    }
}
