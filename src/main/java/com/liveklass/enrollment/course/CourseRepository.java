package com.liveklass.enrollment.course;

import com.liveklass.enrollment.common.exception.CourseNotFoundException;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Page<Course> findByStatus(CourseStatus status, Pageable pageable);

    Page<Course> findByStatusIn(Collection<CourseStatus> statuses, Pageable pageable);

    /** DRAFT(미공개) 강의는 본인이 개설한 것만 목록에서 볼 수 있어야 하므로 개설자 필터를 강제한다. */
    Page<Course> findByStatusAndCreatorId(CourseStatus status, String creatorId, Pageable pageable);

    /**
     * 수강 신청 시 정원 체크 + 신청 생성을 하나의 트랜잭션으로 직렬화하기 위한 비관적 락.
     * 동시에 여러 요청이 마지막 자리를 신청해도 이 락으로 한 번에 하나씩만 처리된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(Long id);

    /**
     * "강의를 조회하고 없으면 404" 조합은 CourseService/EnrollmentService 양쪽에서 각자
     * 반복해서 짜고 있었다. 조회 방식(락 유무)에 따라 예외 던지는 지점만 갈릴 뿐 로직이
     * 완전히 같아서, 반복을 없애기 위해 리포지토리 기본 메서드로 한 곳에 모은다.
     */
    default Course getByIdOrThrow(Long id) {
        return findById(id).orElseThrow(() -> new CourseNotFoundException(id));
    }

    default Course getForUpdateOrThrow(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> new CourseNotFoundException(id));
    }
}
