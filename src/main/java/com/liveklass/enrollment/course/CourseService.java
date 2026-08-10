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
        return CourseDetailResponse.of(course, enrolledCount);
    }

    @Transactional
    public CourseResponse open(Long courseId, String requesterId) {
        Course course = getCourseOrThrow(courseId);
        requireOwner(course, requesterId);
        course.open();
        return CourseResponse.from(course);
    }

    @Transactional
    public CourseResponse close(Long courseId, String requesterId) {
        Course course = getCourseOrThrow(courseId);
        requireOwner(course, requesterId);
        course.close();
        return CourseResponse.from(course);
    }

    private Course getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
    }

    private void requireOwner(Course course, String requesterId) {
        if (!course.isOwnedBy(requesterId)) {
            throw new ForbiddenException("본인이 개설한 강의만 상태를 변경할 수 있습니다.");
        }
    }
}
