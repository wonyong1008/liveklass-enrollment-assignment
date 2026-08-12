package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.course.Course;
import com.liveklass.enrollment.course.CourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 컬럼 applied_at은 MySQL DATETIME(초 단위, 마이크로초 정밀도 없음)이라 같은 초에 등록된
 * 대기자들은 값이 정확히 같아진다. 이때도 FIFO(먼저 등록한 사람 우선)가 지켜지는지 확인한다.
 *
 * 참고: appliedAt 단일 정렬키로 되돌려서 이 테스트를 돌려봤더니 H2에서는 우연히 삽입 순서를
 * 그대로 반환해 이 특정 동률 상황에서는 재현되지 않았다(H2가 MySQL 고유의 타이밍 이슈를
 * 못 잡는 이 프로젝트의 익숙한 패턴). 그래도 ORDER BY에 명시되지 않은 동률 행의 반환 순서는
 * SQL 표준상 정의되어 있지 않으므로, 세컨더리 정렬키(id)는 여전히 필요한 방어적 수정이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnrollmentRepositoryTest {

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    void 대기_등록_시각이_같아도_먼저_저장된_대기자가_승급_1순위다() {
        Course course = new Course("creator-1", "동시등록 테스트", "설명",
                10_000L, 1, LocalDate.now(), LocalDate.now().plusDays(30));
        course.open();
        Long courseId = courseRepository.save(course).getId();

        LocalDateTime sameInstant = LocalDateTime.of(2025, 3, 10, 12, 0, 0);
        Enrollment first = enrollmentRepository.save(Enrollment.waitlisted(courseId, "waiter-first", sameInstant));
        Enrollment second = enrollmentRepository.save(Enrollment.waitlisted(courseId, "waiter-second", sameInstant));

        assertThat(first.getAppliedAt()).isEqualTo(second.getAppliedAt());

        var promoted = enrollmentRepository
                .findFirstByCourseIdAndStatusOrderByAppliedAtAscIdAsc(courseId, EnrollmentStatus.WAITLISTED)
                .orElseThrow();

        assertThat(promoted.getUserId()).isEqualTo("waiter-first");
        assertThat(promoted.getId()).isEqualTo(first.getId());
    }
}
