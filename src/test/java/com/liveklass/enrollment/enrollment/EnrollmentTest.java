package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.common.exception.CancellationPeriodExpiredException;
import com.liveklass.enrollment.common.exception.InvalidEnrollmentStateException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentTest {

    private static final Duration CANCELLABLE_WINDOW = Duration.ofDays(7);

    @Test
    void 신청하면_PENDING_상태다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now());

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    void PENDING에서_결제확정하면_CONFIRMED가_되고_확정시각이_기록된다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now());
        LocalDateTime confirmedAt = LocalDateTime.of(2025, 3, 1, 10, 0);

        enrollment.confirm(confirmedAt);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(enrollment.getConfirmedAt()).isEqualTo(confirmedAt);
    }

    @Test
    void CONFIRMED_상태를_다시_확정할_수_없다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now());
        enrollment.confirm(LocalDateTime.now());

        assertThatThrownBy(() -> enrollment.confirm(LocalDateTime.now()))
                .isInstanceOf(InvalidEnrollmentStateException.class);
    }

    @Test
    void PENDING_상태는_결제_전이라_기간_제한_없이_취소할_수_있다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now());

        enrollment.cancel(LocalDateTime.now().plusYears(1), CANCELLABLE_WINDOW);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void CONFIRMED_후_7일_이내면_취소할_수_있다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now());
        LocalDateTime confirmedAt = LocalDateTime.of(2025, 3, 1, 10, 0);
        enrollment.confirm(confirmedAt);

        enrollment.cancel(confirmedAt.plusDays(6), CANCELLABLE_WINDOW);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(enrollment.getCancelledAt()).isEqualTo(confirmedAt.plusDays(6));
    }

    @Test
    void CONFIRMED_후_7일이_지나면_취소할_수_없다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now());
        LocalDateTime confirmedAt = LocalDateTime.of(2025, 3, 1, 10, 0);
        enrollment.confirm(confirmedAt);

        assertThatThrownBy(() -> enrollment.cancel(confirmedAt.plusDays(8), CANCELLABLE_WINDOW))
                .isInstanceOf(CancellationPeriodExpiredException.class);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }

    @Test
    void 취소된_신청은_다시_취소할_수_없다() {
        Enrollment enrollment = new Enrollment(1L, "user-1", LocalDateTime.now());
        enrollment.cancel(LocalDateTime.now(), CANCELLABLE_WINDOW);

        assertThatThrownBy(() -> enrollment.cancel(LocalDateTime.now(), CANCELLABLE_WINDOW))
                .isInstanceOf(InvalidEnrollmentStateException.class);
    }

    @Test
    void 대기_등록하면_WAITLISTED_상태다() {
        Enrollment enrollment = Enrollment.waitlisted(1L, "user-1", LocalDateTime.now());

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void 대기중인_신청이_승급되면_PENDING이_된다() {
        Enrollment enrollment = Enrollment.waitlisted(1L, "user-1", LocalDateTime.now());

        enrollment.promote();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    void 대기중인_신청은_기간_제한_없이_취소할_수_있다() {
        Enrollment enrollment = Enrollment.waitlisted(1L, "user-1", LocalDateTime.now());

        enrollment.cancel(LocalDateTime.now(), CANCELLABLE_WINDOW);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void 대기중인_신청은_바로_결제확정할_수_없다() {
        Enrollment enrollment = Enrollment.waitlisted(1L, "user-1", LocalDateTime.now());

        assertThatThrownBy(() -> enrollment.confirm(LocalDateTime.now()))
                .isInstanceOf(InvalidEnrollmentStateException.class);
    }
}
