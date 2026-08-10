package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.common.exception.CapacityExceededException;
import com.liveklass.enrollment.common.exception.CourseNotFoundException;
import com.liveklass.enrollment.common.exception.DuplicateEnrollmentException;
import com.liveklass.enrollment.common.exception.EnrollmentNotFoundException;
import com.liveklass.enrollment.common.exception.ForbiddenException;
import com.liveklass.enrollment.common.exception.InvalidCourseStateException;
import com.liveklass.enrollment.common.exception.WaitlistNotAllowedException;
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

        requireNoActiveEnrollment(courseId, userId);

        long currentlyEnrolled = enrollmentRepository.countByCourseIdAndStatusIn(courseId, EnrollmentStatus.SEAT_HOLDING);
        if (currentlyEnrolled >= course.getCapacity()) {
            throw new CapacityExceededException(courseId);
        }

        Enrollment enrollment = new Enrollment(courseId, userId, LocalDateTime.now(clock));
        return EnrollmentResponse.from(enrollmentRepository.save(enrollment));
    }

    /**
     * 정원이 가득 찬 경우에만 대기열에 등록할 수 있다. 좌석이 있는데 대기부터 걸게 하는 것은
     * 사용자에게 불리하므로, 자리가 있으면 바로 신청(apply)하도록 안내한다.
     */
    @Transactional
    public EnrollmentResponse joinWaitlist(String userId, Long courseId) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        if (!course.isOpenForEnrollment()) {
            throw new InvalidCourseStateException("모집 중인 강의만 대기 신청할 수 있습니다. status=" + course.getStatus());
        }

        requireNoActiveEnrollment(courseId, userId);

        long currentlyEnrolled = enrollmentRepository.countByCourseIdAndStatusIn(courseId, EnrollmentStatus.SEAT_HOLDING);
        if (currentlyEnrolled < course.getCapacity()) {
            throw new WaitlistNotAllowedException(courseId);
        }

        Enrollment enrollment = Enrollment.waitlisted(courseId, userId, LocalDateTime.now(clock));
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
        boolean wasHoldingSeat = EnrollmentStatus.SEAT_HOLDING.contains(enrollment.getStatus());

        enrollment.cancel(LocalDateTime.now(clock), Duration.ofDays(cancellableDays));

        if (wasHoldingSeat) {
            promoteNextWaitlisted(enrollment.getCourseId());
        }
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

    /**
     * 좌석을 보유하던 신청이 취소되어 자리가 하나 비었을 때, 가장 먼저 대기열에 등록한 사람을
     * PENDING으로 승급시킨다. 호출 시점에 이미 courseId에 대한 비관적 락을 다시 획득하므로
     * 승급 처리 중 다른 신청/취소/대기 요청과 경합하지 않는다.
     */
    private void promoteNextWaitlisted(Long courseId) {
        courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        enrollmentRepository.findFirstByCourseIdAndStatusOrderByAppliedAtAsc(courseId, EnrollmentStatus.WAITLISTED)
                .ifPresent(Enrollment::promote);
    }

    private void requireNoActiveEnrollment(Long courseId, String userId) {
        enrollmentRepository.findByCourseIdAndUserIdAndStatusIn(courseId, userId, EnrollmentStatus.ACTIVE)
                .ifPresent(e -> {
                    throw new DuplicateEnrollmentException(courseId, userId);
                });
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
