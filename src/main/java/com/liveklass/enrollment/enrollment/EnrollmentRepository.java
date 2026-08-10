package com.liveklass.enrollment.enrollment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long countByCourseIdAndStatusIn(Long courseId, Collection<EnrollmentStatus> statuses);

    long countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    Optional<Enrollment> findByCourseIdAndUserIdAndStatusIn(Long courseId, String userId, Collection<EnrollmentStatus> statuses);

    Page<Enrollment> findByUserId(String userId, Pageable pageable);

    Page<Enrollment> findByCourseId(Long courseId, Pageable pageable);

    /** 좌석이 빌 때 승급시킬 다음 대기자(가장 먼저 대기열에 등록한 사람) */
    Optional<Enrollment> findFirstByCourseIdAndStatusOrderByAppliedAtAsc(Long courseId, EnrollmentStatus status);
}
