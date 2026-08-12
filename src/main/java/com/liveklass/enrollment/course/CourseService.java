package com.liveklass.enrollment.course;

import com.liveklass.enrollment.common.exception.CourseNotFoundException;
import com.liveklass.enrollment.common.exception.ForbiddenException;
import com.liveklass.enrollment.course.dto.CourseCreateRequest;
import com.liveklass.enrollment.course.dto.CourseDetailResponse;
import com.liveklass.enrollment.course.dto.CourseResponse;
import com.liveklass.enrollment.enrollment.EnrollmentRepository;
import com.liveklass.enrollment.enrollment.EnrollmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    /** 상태를 지정하지 않고 목록을 조회할 때 노출해도 되는 상태. DRAFT(미공개)는 제외한다. */
    private static final List<CourseStatus> PUBLIC_STATUSES = List.of(CourseStatus.OPEN, CourseStatus.CLOSED);

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional
    public CourseResponse create(String creatorId, CourseCreateRequest request) {
        Course course = new Course(
                creatorId,
                request.title(),
                request.description(),
                request.price(),
                request.capacity(),
                request.startDate(),
                request.endDate()
        );
        return CourseResponse.from(courseRepository.save(course));
    }

    /**
     * DRAFT(미공개) 강의는 본인이 개설한 것만 보여야 한다. {@code status=DRAFT}로 필터링하면
     * 개설자 필터를 강제하고, 상태를 지정하지 않은 전체 조회에는 공개 상태(OPEN/CLOSED)만
     * 포함시켜 다른 사람의 미공개 강의가 섞여 나오지 않게 한다.
     */
    public Page<CourseResponse> list(CourseStatus status, String requesterId, Pageable pageable) {
        if (status == CourseStatus.DRAFT) {
            return courseRepository.findByStatusAndCreatorId(status, requesterId, pageable).map(CourseResponse::from);
        }
        Page<Course> courses = status != null
                ? courseRepository.findByStatus(status, pageable)
                : courseRepository.findByStatusIn(PUBLIC_STATUSES, pageable);
        return courses.map(CourseResponse::from);
    }

    /** DRAFT(미공개) 강의는 본인만 상세조회할 수 있다. 존재 자체를 숨기기 위해 403이 아니라 404로 응답한다. */
    public CourseDetailResponse getDetail(Long courseId, String requesterId) {
        Course course = courseRepository.getByIdOrThrow(courseId);
        if (course.getStatus() == CourseStatus.DRAFT && !course.isOwnedBy(requesterId)) {
            throw new CourseNotFoundException(courseId);
        }
        int enrolledCount = (int) enrollmentRepository.countByCourseIdAndStatusIn(courseId, EnrollmentStatus.SEAT_HOLDING);
        int waitlistCount = (int) enrollmentRepository.countByCourseIdAndStatus(courseId, EnrollmentStatus.WAITLISTED);
        return CourseDetailResponse.of(course, enrolledCount, waitlistCount);
    }

    /**
     * open()/close()는 apply()/joinWaitlist()/cancel()과 마찬가지로 강의 row를 비관적 락으로
     * 조회한다. 지금은 상태 전이가 멱등적(중복 호출해도 최종 상태가 같음)이라 락 없이도 실제
     * 데이터 손상은 없지만, 이 코드베이스에서 "강의/신청 상태를 읽고 바꾸는" 경로는 전부 락을
     * 쓰는 게 규칙이다 — 이 규칙에서 벗어난 코드가 있으면 나중에 관련 로직이 추가될 때 이미
     * 세 번 반복된 REPEATABLE READ 스냅샷 버그가 재발할 함정이 되므로 미리 통일해둔다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CourseResponse open(Long courseId, String requesterId) {
        Course course = courseRepository.getForUpdateOrThrow(courseId);
        requireOwner(course, requesterId);
        course.open();
        return CourseResponse.from(course);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CourseResponse close(Long courseId, String requesterId) {
        Course course = courseRepository.getForUpdateOrThrow(courseId);
        requireOwner(course, requesterId);
        course.close();
        return CourseResponse.from(course);
    }

    private void requireOwner(Course course, String requesterId) {
        if (!course.isOwnedBy(requesterId)) {
            throw new ForbiddenException("본인이 개설한 강의만 상태를 변경할 수 있습니다.");
        }
    }
}
