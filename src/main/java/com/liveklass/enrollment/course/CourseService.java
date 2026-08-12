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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

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

    public Page<CourseResponse> list(CourseStatus status, Pageable pageable) {
        Page<Course> courses = status != null
                ? courseRepository.findByStatus(status, pageable)
                : courseRepository.findAll(pageable);
        return courses.map(CourseResponse::from);
    }

    public CourseDetailResponse getDetail(Long courseId) {
        Course course = getCourseOrThrow(courseId);
        int enrolledCount = (int) enrollmentRepository.countByCourseIdAndStatusIn(courseId, EnrollmentStatus.SEAT_HOLDING);
        int waitlistCount = (int) enrollmentRepository.countByCourseIdAndStatus(courseId, EnrollmentStatus.WAITLISTED);
        return CourseDetailResponse.of(course, enrolledCount, waitlistCount);
    }

    /**
     * open()/close()는 apply()/joinWaitlist()/cancel()과 마찬가지로 강의 row를 비관적 락으로
     * 조회한다. 지금은 상태 전이가 멱등적(중복 호출해도 최종 상태가 같음)이라 락 없이도 실제
     * 데이터 손상은 없지만, 이 코드베이스에서 "강의/신청 상태를 읽고 바꾸는" 경로는 전부 락을
     * 쓰는 게 규칙이다 — 이 규칙에서 벗어난 코드가 있으면 나중에 관련 로직이 추가될 때 이미
     * 세 번 반복된 REPEATABLE READ 스냅샷 버그(.notes/버그수정기록.md 1, 2, 2-1번)가 재발할
     * 함정이 되므로 미리 통일해둔다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CourseResponse open(Long courseId, String requesterId) {
        Course course = lockCourseOrThrow(courseId);
        requireOwner(course, requesterId);
        course.open();
        return CourseResponse.from(course);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CourseResponse close(Long courseId, String requesterId) {
        Course course = lockCourseOrThrow(courseId);
        requireOwner(course, requesterId);
        course.close();
        return CourseResponse.from(course);
    }

    private Course getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
    }

    private Course lockCourseOrThrow(Long courseId) {
        return courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
    }

    private void requireOwner(Course course, String requesterId) {
        if (!course.isOwnedBy(requesterId)) {
            throw new ForbiddenException("본인이 개설한 강의만 상태를 변경할 수 있습니다.");
        }
    }
}
