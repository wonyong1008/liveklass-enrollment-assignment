package com.liveklass.enrollment.enrollment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long countByCourseIdAndStatusIn(Long courseId, Collection<EnrollmentStatus> statuses);

    Optional<Enrollment> findByCourseIdAndUserIdAndStatusIn(Long courseId, String userId, Collection<EnrollmentStatus> statuses);

    Page<Enrollment> findByUserId(String userId, Pageable pageable);

    Page<Enrollment> findByCourseId(Long courseId, Pageable pageable);
}
