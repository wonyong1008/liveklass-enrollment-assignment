package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.common.exception.CapacityExceededException;
import com.liveklass.enrollment.common.exception.DuplicateEnrollmentException;
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

    /**
     * 정원 경합뿐 아니라, 같은 사용자가 "신청" 버튼을 여러 번 빠르게 눌러 동시에 여러 요청이
     * 들어와도 중복 신청이 생기면 안 된다. apply()가 거는 Course 락이 동일 사용자의 동시
     * 요청도 같은 방식으로 직렬화하는지 검증한다.
     */
    @Test
    void 같은_사용자가_동시에_여러번_신청해도_하나만_성공한다() throws InterruptedException {
        int requestCount = 10;

        Course course = new Course("creator-1", "중복클릭 테스트 강의", "설명",
                BigDecimal.valueOf(10_000), 10, LocalDate.now(), LocalDate.now().plusDays(30));
        course.open();
        Long courseId = courseRepository.save(course).getId();

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();
        List<Throwable> unexpectedFailures = Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.apply("same-user", courseId);
                    successCount.incrementAndGet();
                } catch (DuplicateEnrollmentException e) {
                    duplicateCount.incrementAndGet();
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

        assertThat(completed).isTrue();
        assertThat(unexpectedFailures).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(requestCount - 1);

        long myEnrollmentCount = enrollmentRepository.countByCourseIdAndStatusIn(courseId, EnrollmentStatus.SEAT_HOLDING);
        assertThat(myEnrollmentCount).isEqualTo(1);
    }

    /**
     * 좌석 보유자 여러 명이 동시에 취소되면, 대기자도 그만큼 전부 승급되어야 한다(한 명만
     * 중복 승급되고 나머지는 누락되면 안 됨). cancel()의 첫 조회가 락 없는 SELECT라서
     * REPEATABLE READ 하에서는 이후의 "다음 대기자 조회"가 오래된 스냅샷을 볼 수 있는데,
     * 쓰기 트랜잭션을 READ_COMMITTED로 명시해 이 문제를 막았다(EnrollmentService 클래스
     * 주석 참고).
     */
    @Test
    void 좌석보유자_여러명이_동시취소해도_대기자_전원이_승급된다() throws InterruptedException {
        int capacity = 3;

        Course course = new Course("creator-1", "다중 승급 테스트 강의", "설명",
                BigDecimal.valueOf(10_000), capacity, LocalDate.now(), LocalDate.now().plusDays(30));
        course.open();
        Long courseId = courseRepository.save(course).getId();

        List<Long> holderEnrollmentIds = new java.util.ArrayList<>();
        List<String> holderUserIds = new java.util.ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            String userId = "holder-" + i;
            holderUserIds.add(userId);
            holderEnrollmentIds.add(enrollmentService.apply(userId, courseId).id());
        }

        List<String> waiterUserIds = new java.util.ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            String userId = "waiter-" + i;
            waiterUserIds.add(userId);
            enrollmentService.joinWaitlist(userId, courseId);
        }

        ExecutorService executor = Executors.newFixedThreadPool(capacity);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(capacity);
        List<Throwable> unexpectedFailures = Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < capacity; i++) {
            String userId = holderUserIds.get(i);
            Long enrollmentId = holderEnrollmentIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.cancel(enrollmentId, userId);
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

        assertThat(completed).isTrue();
        assertThat(unexpectedFailures).isEmpty();

        for (String waiterUserId : waiterUserIds) {
            var myEnrollments = enrollmentService.myEnrollments(waiterUserId, org.springframework.data.domain.PageRequest.of(0, 10));
            assertThat(myEnrollments.getContent())
                    .as(waiterUserId + "는 대기 순번대로 반드시 승급되어야 한다")
                    .extracting(dto -> dto.status())
                    .containsExactly(EnrollmentStatus.PENDING);
        }

        long seatHoldingCount = enrollmentRepository.countByCourseIdAndStatusIn(courseId, EnrollmentStatus.SEAT_HOLDING);
        assertThat(seatHoldingCount)
                .as("대기자 전원이 승급됐다면 정원만큼 좌석이 다시 채워져 있어야 한다")
                .isEqualTo(capacity);
    }

    /**
     * 같은 신청 건(enrollmentId)에 취소 요청이 동시에 여러 번 들어와도 좌석은 딱 한 번만
     * 비워져야 한다. getOwnedEnrollmentOrThrow가 Enrollment를 락 없이 조회하면, 동시 요청이
     * 서로의 커밋을 못 보고 둘 다 "좌석을 갖고 있었다"고 판단해 대기자를 중복 승급시켜
     * 정원을 초과할 수 있었다(수정 전 실제로 재현됨).
     */
    @Test
    void 같은_신청건에_동시_취소요청이_와도_좌석은_한번만_비워진다() throws InterruptedException {
        int requestCount = 10;

        Course course = new Course("creator-1", "중복취소 테스트 강의", "설명",
                BigDecimal.valueOf(10_000), 1, LocalDate.now(), LocalDate.now().plusDays(30));
        course.open();
        Long courseId = courseRepository.save(course).getId();

        Long holderEnrollmentId = enrollmentService.apply("holder", courseId).id();
        enrollmentService.joinWaitlist("waiter", courseId);

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> unexpectedFailures = Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.cancel(holderEnrollmentId, "holder");
                    successCount.incrementAndGet();
                } catch (com.liveklass.enrollment.common.exception.InvalidEnrollmentStateException e) {
                    // 이미 다른 스레드가 먼저 취소 처리한 경우 정상적으로 발생
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

        assertThat(completed).isTrue();
        assertThat(unexpectedFailures).isEmpty();
        assertThat(successCount.get()).as("취소 자체는 딱 한 번만 성공해야 한다").isEqualTo(1);

        long seatHoldingCount = enrollmentRepository.countByCourseIdAndStatusIn(courseId, EnrollmentStatus.SEAT_HOLDING);
        assertThat(seatHoldingCount)
                .as("동시 취소 요청이 여러 번 와도 대기자는 한 명만 승급되어 정원을 넘으면 안 된다")
                .isEqualTo(1);
    }
}
