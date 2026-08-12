package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.common.exception.CapacityExceededException;
import com.liveklass.enrollment.common.exception.DuplicateEnrollmentException;
import com.liveklass.enrollment.common.exception.EnrollmentNotFoundException;
import com.liveklass.enrollment.common.exception.ForbiddenException;
import com.liveklass.enrollment.common.exception.InvalidCourseStateException;
import com.liveklass.enrollment.common.exception.WaitlistNotAllowedException;
import com.liveklass.enrollment.course.Course;
import com.liveklass.enrollment.course.CourseRepository;
import com.liveklass.enrollment.enrollment.dto.EnrollmentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 이 서비스는 정원/대기열 정합성을 MySQL의 스냅샷 격리가 아니라 명시적 비관적 락으로 직접
 * 제어한다. MySQL InnoDB의 기본 격리수준인 REPEATABLE READ에서는 트랜잭션의 "첫 읽기"가
 * 스냅샷을 고정하고, 락이 걸리지 않은 일반 SELECT는 그 스냅샷만 본다(락을 건 읽기만 예외적으로
 * 항상 최신 커밋을 본다). 락 없는 조회가 먼저 일어나고 그 뒤에 락을 거는 순서로 로직이 짜여
 * 있으면, 락 이후의 일반 SELECT가 락 획득 이전의 오래된 스냅샷을 보게 되어 방금 커밋된 변경을
 * 놓칠 수 있다. 그래서 쓰기 트랜잭션은 READ_COMMITTED로 명시해 매 쿼리가 항상 최신 커밋을
 * 보도록 통일하고, {@code Course}뿐 아니라 {@code Enrollment} 자체도 변경 시에는 비관적
 * 락으로 조회해 같은 신청 건에 대한 동시 요청(중복 취소/중복 확정)을 직렬화한다.
 */
@Service
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final Clock clock;
    private final long cancellableDays;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                              CourseRepository courseRepository,
                              Clock clock,
                              @Value("${enrollment.cancellable-days:7}") long cancellableDays) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.clock = clock;
        this.cancellableDays = cancellableDays;
    }

    /**
     * 강의 row에 비관적 락을 건 채로 "현재 신청 인원 카운트 -> 정원 비교 -> 신청 생성"을
     * 한 트랜잭션에서 수행한다. 동시에 여러 요청이 마지막 한 자리를 신청해도 이 락 때문에
     * 한 번에 하나씩만 통과하므로 정원 초과(오버셀)가 발생하지 않는다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EnrollmentResponse apply(String userId, Long courseId) {
        CourseSeatSnapshot snapshot = lockCourseForEnrollment(courseId, userId, "신청");

        if (snapshot.currentlyEnrolled() >= snapshot.course().getCapacity()) {
            throw new CapacityExceededException(courseId);
        }

        Enrollment enrollment = new Enrollment(courseId, userId, LocalDateTime.now(clock));
        return EnrollmentResponse.from(enrollmentRepository.save(enrollment));
    }

    /**
     * 정원이 가득 찬 경우에만 대기열에 등록할 수 있다. 좌석이 있는데 대기부터 걸게 하는 것은
     * 사용자에게 불리하므로, 자리가 있으면 바로 신청(apply)하도록 안내한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EnrollmentResponse joinWaitlist(String userId, Long courseId) {
        CourseSeatSnapshot snapshot = lockCourseForEnrollment(courseId, userId, "대기 신청");

        if (snapshot.currentlyEnrolled() < snapshot.course().getCapacity()) {
            throw new WaitlistNotAllowedException(courseId);
        }

        Enrollment enrollment = Enrollment.waitlisted(courseId, userId, LocalDateTime.now(clock));
        return EnrollmentResponse.from(enrollmentRepository.save(enrollment));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EnrollmentResponse confirm(Long enrollmentId, String userId) {
        Enrollment enrollment = getOwnedEnrollmentOrThrow(enrollmentId, userId);
        enrollment.confirm(LocalDateTime.now(clock));
        return EnrollmentResponse.from(enrollment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        Course course = courseRepository.getByIdOrThrow(courseId);
        if (!course.isOwnedBy(requesterId)) {
            throw new ForbiddenException("본인이 개설한 강의의 수강생만 조회할 수 있습니다.");
        }
        return enrollmentRepository.findByCourseId(courseId, pageable).map(EnrollmentResponse::from);
    }

    /**
     * apply/joinWaitlist가 공통으로 필요로 하는 전제조건(락, 모집중 여부, 중복신청 여부)을
     * 확인하고 현재 신청 인원을 함께 돌려준다. 정원과 비교하는 방향(넘으면 안 됨 / 안 넘으면 안 됨)만
     * 호출부에서 갈린다.
     */
    private CourseSeatSnapshot lockCourseForEnrollment(Long courseId, String userId, String action) {
        Course course = courseRepository.getForUpdateOrThrow(courseId);

        if (!course.isOpenForEnrollment()) {
            throw new InvalidCourseStateException("모집 중인 강의만 " + action + "할 수 있습니다. status=" + course.getStatus());
        }

        requireNoActiveEnrollment(courseId, userId);

        long currentlyEnrolled = enrollmentRepository.countByCourseIdAndStatusIn(courseId, EnrollmentStatus.SEAT_HOLDING);
        return new CourseSeatSnapshot(course, currentlyEnrolled);
    }

    private record CourseSeatSnapshot(Course course, long currentlyEnrolled) {
    }

    /**
     * 좌석을 보유하던 신청이 취소되어 자리가 하나 비었을 때, 가장 먼저 대기열에 등록한 사람을
     * PENDING으로 승급시킨다. 호출 시점에 이미 courseId에 대한 비관적 락을 다시 획득하므로
     * 승급 처리 중 다른 신청/취소/대기 요청과 경합하지 않는다. 강의가 이미 모집 마감(CLOSED)된
     * 뒤라면 새로 좌석을 배정하지 않는다 — 대기자는 WAITLISTED로 남는다(apply/joinWaitlist가
     * 모집중인 강의에만 허용되는 것과 같은 규칙을 취소로 인한 자동 승급에도 동일하게 적용).
     */
    private void promoteNextWaitlisted(Long courseId) {
        Course course = courseRepository.getForUpdateOrThrow(courseId);
        if (!course.isOpenForEnrollment()) {
            return;
        }

        enrollmentRepository.findFirstByCourseIdAndStatusOrderByAppliedAtAscIdAsc(courseId, EnrollmentStatus.WAITLISTED)
                .ifPresent(Enrollment::promote);
    }

    private void requireNoActiveEnrollment(Long courseId, String userId) {
        if (enrollmentRepository.existsByCourseIdAndUserIdAndStatusIn(courseId, userId, EnrollmentStatus.ACTIVE)) {
            throw new DuplicateEnrollmentException(courseId, userId);
        }
    }

    /**
     * confirm/cancel 대상 신청 건을 비관적 락으로 조회한다. 락 없는 findById를 쓰면 같은
     * 신청 건에 대한 동시 요청(중복 취소/중복 확정)이 서로의 커밋을 못 보고 각자 "처리 전"
     * 상태로 판단해, 대기열 승급 중복 또는 lost update로 이어질 수 있다.
     */
    private Enrollment getOwnedEnrollmentOrThrow(Long enrollmentId, String userId) {
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        if (!enrollment.isOwnedBy(userId)) {
            throw new ForbiddenException("본인의 수강 신청만 처리할 수 있습니다.");
        }
        return enrollment;
    }
}
