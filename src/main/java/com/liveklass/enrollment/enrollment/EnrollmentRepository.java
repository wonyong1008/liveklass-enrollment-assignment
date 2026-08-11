package com.liveklass.enrollment.enrollment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long countByCourseIdAndStatusIn(Long courseId, Collection<EnrollmentStatus> statuses);

    long countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    List<Enrollment> findByCourseIdAndUserIdAndStatusIn(Long courseId, String userId, Collection<EnrollmentStatus> statuses);

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

    /** 좌석이 빌 때 승급시킬 다음 대기자(가장 먼저 대기열에 등록한 사람) */
    Optional<Enrollment> findFirstByCourseIdAndStatusOrderByAppliedAtAsc(Long courseId, EnrollmentStatus status);
}
