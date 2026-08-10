package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.common.exception.CapacityExceededException;
import com.liveklass.enrollment.common.exception.CourseNotFoundException;
import com.liveklass.enrollment.common.exception.DuplicateEnrollmentException;
import com.liveklass.enrollment.common.exception.EnrollmentNotFoundException;
import com.liveklass.enrollment.common.exception.ForbiddenException;
import com.liveklass.enrollment.common.exception.InvalidCourseStateException;
import com.liveklass.enrollment.course.Course;
import com.liveklass.enrollment.course.CourseRepository;
import com.liveklass.enrollment.enrollment.dto.EnrollmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final Clock clock;

    @Value("${enrollment.cancellable-days:7}")
    private long cancellableDays;

    /**
     * 강의 row에 비관적 락을 건 채로 "현재 신청 인원 카운트 -> 정원 비교 -> 신청 생성"을
     * 한 트랜잭션에서 수행한다. 동시에 여러 요청이 마지막 한 자리를 신청해도 이 락 때문에
     * 한 번에 하나씩만 통과하므로 정원 초과(오버셀)가 발생하지 않는다.
     */
    @Transactional
    public EnrollmentResponse apply(String userId, Long courseId) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        if (!course.isOpenForEnrollment()) {
            throw new InvalidCourseStateException("모집 중인 강의만 신청할 수 있습니다. status=" + course.getStatus());
        }

        enrollmentRepository.findByCourseIdAndUserIdAndStatusIn(courseId, userId, EnrollmentStatus.SEAT_HOLDING)
                .ifPresent(e -> {
                    throw new DuplicateEnrollmentException(courseId, userId);
                });

        long currentlyEnrolled = enrollmentRepository.countByCourseIdAndStatusIn(courseId, EnrollmentStatus.SEAT_HOLDING);
        if (currentlyEnrolled >= course.getCapacity()) {
            throw new CapacityExceededException(courseId);
        }

        Enrollment enrollment = new Enrollment(courseId, userId, LocalDateTime.now(clock));
        return EnrollmentResponse.from(enrollmentRepository.save(enrollment));
    }

    @Transactional
    public EnrollmentResponse confirm(Long enrollmentId, String userId) {
        Enrollment enrollment = getOwnedEnrollmentOrThrow(enrollmentId, userId);
        enrollment.confirm(LocalDateTime.now(clock));
        return EnrollmentResponse.from(enrollment);
    }

    @Transactional
    public EnrollmentResponse cancel(Long enrollmentId, String userId) {
        Enrollment enrollment = getOwnedEnrollmentOrThrow(enrollmentId, userId);
        enrollment.cancel(LocalDateTime.now(clock), Duration.ofDays(cancellableDays));
        return EnrollmentResponse.from(enrollment);
    }

    public Page<EnrollmentResponse> myEnrollments(String userId, Pageable pageable) {
        return enrollmentRepository.findByUserId(userId, pageable).map(EnrollmentResponse::from);
    }

    /** 강의별 수강생 목록 조회 (크리에이터 전용) */
    public Page<EnrollmentResponse> courseEnrollments(Long courseId, String requesterId, Pageable pageable) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        if (!course.isOwnedBy(requesterId)) {
            throw new ForbiddenException("본인이 개설한 강의의 수강생만 조회할 수 있습니다.");
        }
        return enrollmentRepository.findByCourseId(courseId, pageable).map(EnrollmentResponse::from);
    }

    private Enrollment getOwnedEnrollmentOrThrow(Long enrollmentId, String userId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        if (!enrollment.isOwnedBy(userId)) {
            throw new ForbiddenException("본인의 수강 신청만 처리할 수 있습니다.");
        }
        return enrollment;
    }
}
