package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.enrollment.dto.EnrollmentRequest;
import com.liveklass.enrollment.enrollment.dto.EnrollmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/api/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse apply(@RequestHeader("X-USER-ID") String userId,
                                     @Valid @RequestBody EnrollmentRequest request) {
        return enrollmentService.apply(userId, request.courseId());
    }

    @PostMapping("/api/enrollments/{enrollmentId}/confirm")
    public EnrollmentResponse confirm(@PathVariable Long enrollmentId,
                                       @RequestHeader("X-USER-ID") String userId) {
        return enrollmentService.confirm(enrollmentId, userId);
    }

    @PostMapping("/api/enrollments/{enrollmentId}/cancel")
    public EnrollmentResponse cancel(@PathVariable Long enrollmentId,
                                      @RequestHeader("X-USER-ID") String userId) {
        return enrollmentService.cancel(enrollmentId, userId);
    }

    @GetMapping("/api/enrollments/me")
    public Page<EnrollmentResponse> myEnrollments(@RequestHeader("X-USER-ID") String userId,
                                                   Pageable pageable) {
        return enrollmentService.myEnrollments(userId, pageable);
    }

    @GetMapping("/api/courses/{courseId}/enrollments")
    public Page<EnrollmentResponse> courseEnrollments(@PathVariable Long courseId,
                                                        @RequestHeader("X-USER-ID") String requesterId,
                                                        Pageable pageable) {
        return enrollmentService.courseEnrollments(courseId, requesterId, pageable);
    }
}
