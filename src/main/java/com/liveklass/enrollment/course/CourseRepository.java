package com.liveklass.enrollment.course;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Page<Course> findByStatus(CourseStatus status, Pageable pageable);

    /**
     * 수강 신청 시 정원 체크 + 신청 생성을 하나의 트랜잭션으로 직렬화하기 위한 비관적 락.
     * 동시에 여러 요청이 마지막 자리를 신청해도 이 락으로 한 번에 하나씩만 처리된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(Long id);
}
