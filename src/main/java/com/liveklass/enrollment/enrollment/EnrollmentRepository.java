package com.liveklass.enrollment.enrollment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long countByCourseIdAndStatusIn(Long courseId, Collection<EnrollmentStatus> statuses);

    long countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    boolean existsByCourseIdAndUserIdAndStatusIn(Long courseId, String userId, Collection<EnrollmentStatus> statuses);

    /**
     * confirm/cancel처럼 신청 건 자체를 변경하는 작업 전용. 락 없는 findById로 조회하면
     * 같은 신청 건에 대한 동시 요청(중복 취소/중복 확정)이 서로의 커밋을 못 보고 각자
     * "처리 전" 상태로 판단해 승급 중복·lost update로 이어질 수 있어 락을 건다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Enrollment e where e.id = :id")
    Optional<Enrollment> findByIdForUpdate(Long id);

    Page<Enrollment> findByUserId(String userId, Pageable pageable);

    Page<Enrollment> findByCourseId(Long courseId, Pageable pageable);

    /**
     * 좌석이 빌 때 승급시킬 다음 대기자(가장 먼저 대기열에 등록한 사람). 락 없이 조회하면
     * 승급 대상자가 "동시에 자기 자신의 대기 신청을 취소"하는 경우와 경합해, 이미 취소된
     * 신청을 다시 PENDING으로 되살릴 수 있어 락을 건다.
     *
     * appliedAt만으로 정렬하면 안 된다 — DB 컬럼이 MySQL DATETIME(초 단위, 마이크로초 정밀도
     * 없음)이라 같은 초에 여러 명이 대기 등록하면 값이 정확히 같아진다. 이 경우 정렬 기준이
     * 하나뿐이면 동률 행의 반환 순서를 MySQL이 보장하지 않아 FIFO가 깨질 수 있다. auto-increment
     * id는 이 락으로 직렬화된 등록 순서를 정확히 반영하므로 세컨더리 정렬키로 추가해 동률을 깬다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Enrollment> findFirstByCourseIdAndStatusOrderByAppliedAtAscIdAsc(Long courseId, EnrollmentStatus status);
}
