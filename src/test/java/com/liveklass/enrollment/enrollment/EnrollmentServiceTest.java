package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.common.exception.CancellationPeriodExpiredException;
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
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    // getByIdOrThrow/getForUpdateOrThrow는 CourseRepository의 default 메서드다. Mockito는
    // 기본적으로 default 메서드도 스텁 없이 호출하면 null을 반환하고 실제 본문(findById/
    // findByIdForUpdate 위임)을 실행하지 않으므로, CALLS_REAL_METHODS로 실제 로직이 동작하게 한다.
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private CourseRepository courseRepository;

    private EnrollmentService enrollmentService;
    private final Clock clock = Clock.fixed(Instant.parse("2025-03-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(enrollmentRepository, courseRepository, clock, 7L);
    }

    private Course openCourse(int capacity) {
        Course course = new Course("creator-1", "제목", "설명", 10_000L, capacity,
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
        Course draftCourse = new Course("creator-1", "제목", "설명", 10L, 10,
                LocalDate.now(), LocalDate.now().plusDays(30));
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(draftCourse));

        assertThatThrownBy(() -> enrollmentService.apply("user-1", 1L))
                .isInstanceOf(InvalidCourseStateException.class);
    }

    @Test
    void 이미_신청한_강의는_중복신청할_수_없다() {
        Course course = openCourse(10);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByCourseIdAndUserIdAndStatusIn(any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> enrollmentService.apply("user-1", 1L))
                .isInstanceOf(DuplicateEnrollmentException.class);
    }

    @Test
    void 정원이_가득_차면_신청할_수_없다() {
        Course course = openCourse(2);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        lenient().when(enrollmentRepository.existsByCourseIdAndUserIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(enrollmentRepository.countByCourseIdAndStatusIn(any(), any())).thenReturn(2L);

        assertThatThrownBy(() -> enrollmentService.apply("user-1", 1L))
                .isInstanceOf(CapacityExceededException.class);
    }

    @Test
    void 정원이_남아있으면_신청에_성공한다() {
        Course course = openCourse(2);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        lenient().when(enrollmentRepository.existsByCourseIdAndUserIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(enrollmentRepository.countByCourseIdAndStatusIn(any(), any())).thenReturn(1L);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = enrollmentService.apply("user-1", 1L);

        assertThat(response.status()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(response.userId()).isEqualTo("user-1");
    }

    @Test
    void 본인_신청건이_아니면_확정할_수_없다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now());
        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.confirm(10L, "다른-사람"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 본인_신청건은_결제확정할_수_있다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now());
        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(enrollment));

        var response = enrollmentService.confirm(10L, "user-1");

        assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }

    @Test
    void 본인_신청건이_아니면_취소할_수_없다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now());
        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.cancel(10L, "다른-사람"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 결제_전_신청건은_바로_취소할_수_있다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now(clock));
        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(enrollment));
        lenient().when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(openCourse(2)));

        var response = enrollmentService.cancel(10L, "user-1");

        assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void 결제확정_후_취소가능_기간_이내면_취소할_수_있다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now(clock).minusDays(10));
        enrollment.confirm(LocalDateTime.now(clock).minusDays(3));
        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(enrollment));
        lenient().when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(openCourse(2)));

        var response = enrollmentService.cancel(10L, "user-1");

        assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void 좌석을_보유한_신청을_취소하면_대기_1순위가_자동승급된다() {
        Enrollment holder = new Enrollment(1L, "user-1", LocalDateTime.now(clock));
        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(holder));
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(openCourse(1)));

        Enrollment waitlisted = Enrollment.waitlisted(1L, "user-2", LocalDateTime.now(clock).minusMinutes(5));
        when(enrollmentRepository.findFirstByCourseIdAndStatusOrderByAppliedAtAscIdAsc(1L, EnrollmentStatus.WAITLISTED))
                .thenReturn(Optional.of(waitlisted));

        enrollmentService.cancel(10L, "user-1");

        assertThat(waitlisted.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    void 대기자가_없으면_취소해도_아무도_승급되지_않는다() {
        Enrollment holder = new Enrollment(1L, "user-1", LocalDateTime.now(clock));
        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(holder));
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(openCourse(1)));
        when(enrollmentRepository.findFirstByCourseIdAndStatusOrderByAppliedAtAscIdAsc(1L, EnrollmentStatus.WAITLISTED))
                .thenReturn(Optional.empty());

        var response = enrollmentService.cancel(10L, "user-1");

        assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void 대기신청을_취소하면_승급로직이_실행되지_않는다() {
        Enrollment waitlisted = Enrollment.waitlisted(1L, "user-1", LocalDateTime.now(clock));
        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(waitlisted));

        var response = enrollmentService.cancel(10L, "user-1");

        assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        verify(courseRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void 결제확정_후_취소가능_기간이_지나면_취소할_수_없다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now(clock).minusDays(10));
        enrollment.confirm(LocalDateTime.now(clock).minusDays(8));
        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.cancel(10L, "user-1"))
                .isInstanceOf(CancellationPeriodExpiredException.class);
    }

    @Test
    void 내_신청_목록을_조회한다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now());
        when(enrollmentRepository.findByUserId("user-1", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(enrollment)));

        var page = enrollmentService.myEnrollments("user-1", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).userId()).isEqualTo("user-1");
    }

    @Test
    void 개설자가_아니면_강의별_수강생_목록을_조회할_수_없다() {
        Course course = openCourse(10);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> enrollmentService.courseEnrollments(1L, "다른-사람", PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 개설자는_강의별_수강생_목록을_조회할_수_있다() {
        Course course = openCourse(10);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseId(1L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(new Enrollment(1L, "user-1", LocalDateTime.now()))));

        var page = enrollmentService.courseEnrollments(1L, "creator-1", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void 정원이_남아있으면_대기신청할_수_없다() {
        Course course = openCourse(2);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        lenient().when(enrollmentRepository.existsByCourseIdAndUserIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(enrollmentRepository.countByCourseIdAndStatusIn(any(), any())).thenReturn(1L);

        assertThatThrownBy(() -> enrollmentService.joinWaitlist("user-1", 1L))
                .isInstanceOf(com.liveklass.enrollment.common.exception.WaitlistNotAllowedException.class);
    }

    @Test
    void 정원이_가득_차면_대기신청할_수_있다() {
        Course course = openCourse(2);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        lenient().when(enrollmentRepository.existsByCourseIdAndUserIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);
        when(enrollmentRepository.countByCourseIdAndStatusIn(any(), any())).thenReturn(2L);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = enrollmentService.joinWaitlist("user-1", 1L);

        assertThat(response.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void 모집중이_아닌_강의는_대기신청할_수_없다() {
        Course draftCourse = new Course("creator-1", "제목", "설명", 10L, 10,
                LocalDate.now(), LocalDate.now().plusDays(30));
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(draftCourse));

        assertThatThrownBy(() -> enrollmentService.joinWaitlist("user-1", 1L))
                .isInstanceOf(InvalidCourseStateException.class);
    }

    @Test
    void 이미_대기중이면_중복으로_대기신청할_수_없다() {
        Course course = openCourse(1);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByCourseIdAndUserIdAndStatusIn(any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> enrollmentService.joinWaitlist("user-1", 1L))
                .isInstanceOf(DuplicateEnrollmentException.class);
    }

    @Test
    void 이미_취소된_신청을_다시_취소하면_승급이_중복실행되지_않는다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now(clock));
        enrollment.cancel(LocalDateTime.now(clock), java.time.Duration.ofDays(7));
        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.cancel(10L, "user-1"))
                .isInstanceOf(com.liveklass.enrollment.common.exception.InvalidEnrollmentStateException.class);
        verify(courseRepository, never()).findByIdForUpdate(any());
    }
}
