package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.common.exception.CapacityExceededException;
import com.liveklass.enrollment.common.exception.CourseNotFoundException;
import com.liveklass.enrollment.common.exception.DuplicateEnrollmentException;
import com.liveklass.enrollment.common.exception.ForbiddenException;
import com.liveklass.enrollment.common.exception.InvalidCourseStateException;
import com.liveklass.enrollment.course.Course;
import com.liveklass.enrollment.course.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    private EnrollmentService enrollmentService;
    private final Clock clock = Clock.fixed(Instant.parse("2025-03-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(enrollmentRepository, courseRepository, clock);
        ReflectionTestUtils.setField(enrollmentService, "cancellableDays", 7L);
    }

    private Course openCourse(int capacity) {
        Course course = new Course("creator-1", "제목", "설명", BigDecimal.valueOf(10_000), capacity,
                LocalDate.now(), LocalDate.now().plusDays(30));
        course.open();
        return course;
    }

    @Test
    void 강의가_없으면_신청할_수_없다() {
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.apply("user-1", 1L))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void 모집중이_아닌_강의는_신청할_수_없다() {
        Course draftCourse = new Course("creator-1", "제목", "설명", BigDecimal.TEN, 10,
                LocalDate.now(), LocalDate.now().plusDays(30));
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(draftCourse));

        assertThatThrownBy(() -> enrollmentService.apply("user-1", 1L))
                .isInstanceOf(InvalidCourseStateException.class);
    }

    @Test
    void 이미_신청한_강의는_중복신청할_수_없다() {
        Course course = openCourse(10);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndUserIdAndStatusIn(any(), any(), any()))
                .thenReturn(Optional.of(new Enrollment(1L, "user-1", java.time.LocalDateTime.now())));

        assertThatThrownBy(() -> enrollmentService.apply("user-1", 1L))
                .isInstanceOf(DuplicateEnrollmentException.class);
    }

    @Test
    void 정원이_가득_차면_신청할_수_없다() {
        Course course = openCourse(2);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        lenient().when(enrollmentRepository.findByCourseIdAndUserIdAndStatusIn(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(enrollmentRepository.countByCourseIdAndStatusIn(any(), any())).thenReturn(2L);

        assertThatThrownBy(() -> enrollmentService.apply("user-1", 1L))
                .isInstanceOf(CapacityExceededException.class);
    }

    @Test
    void 정원이_남아있으면_신청에_성공한다() {
        Course course = openCourse(2);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        lenient().when(enrollmentRepository.findByCourseIdAndUserIdAndStatusIn(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(enrollmentRepository.countByCourseIdAndStatusIn(any(), any())).thenReturn(1L);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = enrollmentService.apply("user-1", 1L);

        assertThat(response.status()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(response.userId()).isEqualTo("user-1");
    }

    @Test
    void 본인_신청건이_아니면_확정할_수_없다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", java.time.LocalDateTime.now());
        when(enrollmentRepository.findById(10L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.confirm(10L, "다른-사람"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 본인_신청건은_결제확정할_수_있다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", java.time.LocalDateTime.now());
        when(enrollmentRepository.findById(10L)).thenReturn(Optional.of(enrollment));

        var response = enrollmentService.confirm(10L, "user-1");

        assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }
}
