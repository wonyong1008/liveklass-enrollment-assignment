package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.enrollment.dto.EnrollmentRequest;
import com.liveklass.enrollment.enrollment.dto.EnrollmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "수강 신청", description = "수강 신청/결제확정/취소 및 신청 내역 조회")
@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @Operation(summary = "수강 신청", description = "모집 중인 강의에 신청한다(PENDING). 정원이 가득 찬 경우 409를 반환한다.")
    @PostMapping("/api/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse apply(Authentication authentication,
                                     @Valid @RequestBody EnrollmentRequest request) {
        return enrollmentService.apply(authentication.getName(), request.courseId());
    }

    @Operation(summary = "결제 확정", description = "PENDING -> CONFIRMED. 신청자 본인만 호출할 수 있다.")
    @PostMapping("/api/enrollments/{enrollmentId}/confirm")
    public EnrollmentResponse confirm(@PathVariable Long enrollmentId, Authentication authentication) {
        return enrollmentService.confirm(enrollmentId, authentication.getName());
    }

    @Operation(summary = "수강 취소", description = "결제 전(PENDING)은 언제든, 결제 확정 후(CONFIRMED)는 취소 가능 기간 이내에만 취소할 수 있다.")
    @PostMapping("/api/enrollments/{enrollmentId}/cancel")
    public EnrollmentResponse cancel(@PathVariable Long enrollmentId, Authentication authentication) {
        return enrollmentService.cancel(enrollmentId, authentication.getName());
    }

    @Operation(summary = "내 수강 신청 목록 조회")
    @GetMapping("/api/enrollments/me")
    public Page<EnrollmentResponse> myEnrollments(Authentication authentication, Pageable pageable) {
        return enrollmentService.myEnrollments(authentication.getName(), pageable);
    }

    @Operation(summary = "강의별 수강생 목록 조회", description = "강의 개설자 본인만 호출할 수 있다.")
    @GetMapping("/api/courses/{courseId}/enrollments")
    public Page<EnrollmentResponse> courseEnrollments(@PathVariable Long courseId,
                                                        Authentication authentication,
                                                        Pageable pageable) {
        return enrollmentService.courseEnrollments(courseId, authentication.getName(), pageable);
    }
}
