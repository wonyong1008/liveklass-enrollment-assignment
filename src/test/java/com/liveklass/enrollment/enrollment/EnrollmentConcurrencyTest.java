package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.common.exception.CapacityExceededException;
import com.liveklass.enrollment.course.Course;
import com.liveklass.enrollment.course.CourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 과제 A의 핵심 요구사항: "동시에 여러 사람이 마지막 자리에 신청하는 경우"에도 정원을
 * 초과해서는 안 된다. Course row에 건 비관적 락(CourseRepository#findByIdForUpdate)이
 * 실제로 동시 요청을 직렬화하는지를 이 테스트로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class EnrollmentConcurrencyTest {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Test
    void 동시에_정원보다_많은_인원이_신청해도_확정_인원은_정원을_넘지_않는다() throws InterruptedException {
        int capacity = 5;
        int requestCount = 30;

        Course course = new Course("creator-1", "동시성 테스트 강의", "설명",
                BigDecimal.valueOf(10_000), capacity, LocalDate.now(), LocalDate.now().plusDays(30));
        course.open();
        Long courseId = courseRepository.save(course).getId();

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger capacityExceededCount = new AtomicInteger();
        List<Throwable> unexpectedFailures = Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < requestCount; i++) {
            String userId = "user-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.apply(userId, courseId);
                    successCount.incrementAndGet();
                } catch (CapacityExceededException e) {
                    capacityExceededCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedFailures.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("모든 요청이 시간 내에 끝나야 한다").isTrue();
        assertThat(unexpectedFailures).as("정원초과 외의 예외는 없어야 한다").isEmpty();
        assertThat(successCount.get()).isEqualTo(capacity);
        assertThat(capacityExceededCount.get()).isEqualTo(requestCount - capacity);

        long seatHoldingCount = enrollmentRepository.countByCourseIdAndStatusIn(courseId, EnrollmentStatus.SEAT_HOLDING);
        assertThat(seatHoldingCount)
                .as("실제 DB에 반영된 신청 건수도 정원을 넘으면 안 된다")
                .isEqualTo(capacity);
    }
}
