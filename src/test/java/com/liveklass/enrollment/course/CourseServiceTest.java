package com.liveklass.enrollment.course;

import com.liveklass.enrollment.common.exception.CourseNotFoundException;
import com.liveklass.enrollment.common.exception.ForbiddenException;
import com.liveklass.enrollment.course.dto.CourseCreateRequest;
import com.liveklass.enrollment.course.dto.CourseDetailResponse;
import com.liveklass.enrollment.enrollment.EnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    // getByIdOrThrow/getForUpdateOrThrow는 CourseRepository의 default 메서드다. Mockito는
    // 기본적으로 default 메서드도 스텁 없이 호출하면 null을 반환하고 실제 본문(findById 위임)을
    // 실행하지 않으므로, CALLS_REAL_METHODS로 실제 default 메서드 로직이 동작하게 한다.
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    private CourseService courseService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        courseService = new CourseService(courseRepository, enrollmentRepository);
    }

    private Course sampleCourse(String creatorId, int capacity) {
        return new Course(creatorId, "제목", "설명", 10_000L, capacity,
                LocalDate.now(), LocalDate.now().plusDays(30));
    }

    @Test
    void 강의를_생성하면_저장한다() {
        CourseCreateRequest request = new CourseCreateRequest(
                "제목", "설명", 10_000L, 20, LocalDate.now(), LocalDate.now().plusDays(30));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = courseService.create("creator-1", request);

        assertThat(response.creatorId()).isEqualTo("creator-1");
        assertThat(response.status()).isEqualTo(CourseStatus.DRAFT);
    }

    @Test
    void 존재하지_않는_강의_상세조회는_예외() {
        when(courseRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getDetail(1L, "creator-1"))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void 강의_상세조회시_신청인원과_대기인원을_함께_반환한다() {
        Course course = sampleCourse("creator-1", 20);
        course.open();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.countByCourseIdAndStatusIn(eq(1L), anyCollection())).thenReturn(5L);
        when(enrollmentRepository.countByCourseIdAndStatus(1L, com.liveklass.enrollment.enrollment.EnrollmentStatus.WAITLISTED))
                .thenReturn(3L);

        CourseDetailResponse detail = courseService.getDetail(1L, "다른-사람");

        assertThat(detail.enrolledCount()).isEqualTo(5);
        assertThat(detail.remainingSeats()).isEqualTo(15);
        assertThat(detail.waitlistCount()).isEqualTo(3);
    }

    @Test
    void DRAFT_강의는_개설자_본인만_상세조회할_수_있다() {
        Course course = sampleCourse("creator-1", 20);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.getDetail(1L, "다른-사람"))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void DRAFT_강의는_개설자_본인은_상세조회할_수_있다() {
        Course course = sampleCourse("creator-1", 20);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.countByCourseIdAndStatusIn(eq(1L), anyCollection())).thenReturn(0L);
        when(enrollmentRepository.countByCourseIdAndStatus(eq(1L), any())).thenReturn(0L);

        CourseDetailResponse detail = courseService.getDetail(1L, "creator-1");

        assertThat(detail.creatorId()).isEqualTo("creator-1");
    }

    @Test
    void 개설자가_아니면_강의를_열_수_없다() {
        Course course = sampleCourse("creator-1", 20);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.open(1L, "다른-사람"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 개설자는_강의를_열_수_있다() {
        Course course = sampleCourse("creator-1", 20);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));

        var response = courseService.open(1L, "creator-1");

        assertThat(response.status()).isEqualTo(CourseStatus.OPEN);
        verify(courseRepository).findByIdForUpdate(1L);
    }

    @Test
    void DRAFT로_필터링하면_본인이_개설한_강의만_조회한다() {
        Course course = sampleCourse("creator-1", 20);
        when(courseRepository.findByStatusAndCreatorId(CourseStatus.DRAFT, "creator-1", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(course)));

        var page = courseService.list(CourseStatus.DRAFT, "creator-1", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        verify(courseRepository, never()).findByStatus(any(), any());
    }

    @Test
    void OPEN으로_필터링하면_findByStatus로_전체_조회한다() {
        Course course = sampleCourse("creator-1", 20);
        course.open();
        when(courseRepository.findByStatus(CourseStatus.OPEN, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(course)));

        var page = courseService.list(CourseStatus.OPEN, "다른-사람", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        verify(courseRepository, never()).findAll(any(PageRequest.class));
    }

    @Test
    void 상태_필터가_없으면_공개상태만_조회한다() {
        Course course = sampleCourse("creator-1", 20);
        course.open();
        when(courseRepository.findByStatusIn(List.of(CourseStatus.OPEN, CourseStatus.CLOSED), PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(course)));

        var page = courseService.list(null, "다른-사람", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        verify(courseRepository, never()).findAll(any(PageRequest.class));
    }

    @Test
    void 개설자가_아니면_강의를_닫을_수_없다() {
        Course course = sampleCourse("creator-1", 20);
        course.open();
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.close(1L, "다른-사람"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 개설자는_강의를_닫을_수_있다() {
        Course course = sampleCourse("creator-1", 20);
        course.open();
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));

        var response = courseService.close(1L, "creator-1");

        assertThat(response.status()).isEqualTo(CourseStatus.CLOSED);
    }
}
