package com.liveklass.enrollment.course;

import com.liveklass.enrollment.common.exception.InvalidCourseStateException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseTest {

    private Course newCourse(int capacity) {
        return new Course(
                "creator-1", "스프링 부트 실전", "설명",
                BigDecimal.valueOf(50_000),
                capacity,
                LocalDate.now(),
                LocalDate.now().plusMonths(1)
        );
    }

    @Test
    void 강의를_생성하면_DRAFT_상태다() {
        Course course = newCourse(10);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.DRAFT);
        assertThat(course.isOpenForEnrollment()).isFalse();
    }

    @Test
    void 정원이_0이하면_생성할_수_없다() {
        assertThatThrownBy(() -> newCourse(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 종료일이_시작일보다_빠르면_생성할_수_없다() {
        assertThatThrownBy(() -> new Course(
                "creator-1", "제목", "설명", BigDecimal.TEN, 10,
                LocalDate.now(), LocalDate.now().minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    class 상태전이 {

        @Test
        void DRAFT에서_OPEN으로_전이할_수_있다() {
            Course course = newCourse(10);

            course.open();

            assertThat(course.getStatus()).isEqualTo(CourseStatus.OPEN);
            assertThat(course.isOpenForEnrollment()).isTrue();
        }

        @Test
        void OPEN에서_CLOSED로_전이할_수_있다() {
            Course course = newCourse(10);
            course.open();

            course.close();

            assertThat(course.getStatus()).isEqualTo(CourseStatus.CLOSED);
        }

        @Test
        void DRAFT에서_바로_CLOSED로_전이할_수_없다() {
            Course course = newCourse(10);

            assertThatThrownBy(course::close)
                    .isInstanceOf(InvalidCourseStateException.class);
        }

        @Test
        void CLOSED에서는_다시_OPEN으로_전이할_수_없다() {
            Course course = newCourse(10);
            course.open();
            course.close();

            assertThatThrownBy(course::open)
                    .isInstanceOf(InvalidCourseStateException.class);
        }
    }
}
