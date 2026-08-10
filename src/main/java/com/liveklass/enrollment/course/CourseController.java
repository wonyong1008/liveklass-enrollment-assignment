package com.liveklass.enrollment.course;

import com.liveklass.enrollment.course.dto.CourseCreateRequest;
import com.liveklass.enrollment.course.dto.CourseDetailResponse;
import com.liveklass.enrollment.course.dto.CourseResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse create(@RequestHeader("X-USER-ID") String creatorId,
                                  @Valid @RequestBody CourseCreateRequest request) {
        return courseService.create(creatorId, request);
    }

    @GetMapping
    public Page<CourseResponse> list(@RequestParam(required = false) CourseStatus status,
                                      Pageable pageable) {
        return courseService.list(status, pageable);
    }

    @GetMapping("/{courseId}")
    public CourseDetailResponse getDetail(@PathVariable Long courseId) {
        return courseService.getDetail(courseId);
    }

    @PostMapping("/{courseId}/open")
    public CourseResponse open(@PathVariable Long courseId,
                                @RequestHeader("X-USER-ID") String requesterId) {
        return courseService.open(courseId, requesterId);
    }

    @PostMapping("/{courseId}/close")
    public CourseResponse close(@PathVariable Long courseId,
                                 @RequestHeader("X-USER-ID") String requesterId) {
        return courseService.close(courseId, requesterId);
    }
}
