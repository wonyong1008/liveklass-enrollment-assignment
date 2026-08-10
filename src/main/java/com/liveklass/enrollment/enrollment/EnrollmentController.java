package com.liveklass.enrollment.enrollment;

import com.liveklass.enrollment.enrollment.dto.EnrollmentRequest;
import com.liveklass.enrollment.enrollment.dto.EnrollmentResponse;
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

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/api/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse apply(Authentication authentication,
                                     @Valid @RequestBody EnrollmentRequest request) {
        return enrollmentService.apply(authentication.getName(), request.courseId());
    }

    @PostMapping("/api/enrollments/{enrollmentId}/confirm")
    public EnrollmentResponse confirm(@PathVariable Long enrollmentId, Authentication authentication) {
        return enrollmentService.confirm(enrollmentId, authentication.getName());
    }

    @PostMapping("/api/enrollments/{enrollmentId}/cancel")
    public EnrollmentResponse cancel(@PathVariable Long enrollmentId, Authentication authentication) {
        return enrollmentService.cancel(enrollmentId, authentication.getName());
    }

    @GetMapping("/api/enrollments/me")
    public Page<EnrollmentResponse> myEnrollments(Authentication authentication, Pageable pageable) {
        return enrollmentService.myEnrollments(authentication.getName(), pageable);
    }

    @GetMapping("/api/courses/{courseId}/enrollments")
    public Page<EnrollmentResponse> courseEnrollments(@PathVariable Long courseId,
                                                        Authentication authentication,
                                                        Pageable pageable) {
        return enrollmentService.courseEnrollments(courseId, authentication.getName(), pageable);
    }
}
