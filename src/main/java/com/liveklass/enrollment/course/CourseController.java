package com.liveklass.enrollment.course;

import com.liveklass.enrollment.common.PageResponse;
import com.liveklass.enrollment.course.dto.CourseCreateRequest;
import com.liveklass.enrollment.course.dto.CourseDetailResponse;
import com.liveklass.enrollment.course.dto.CourseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "강의", description = "강의 개설 및 모집 상태 관리")
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "강의 개설", description = "DRAFT 상태로 강의를 생성한다. 호출자가 개설자(creator)가 된다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse create(Authentication authentication,
                                  @Valid @RequestBody CourseCreateRequest request) {
        return courseService.create(authentication.getName(), request);
    }

    @Operation(summary = "강의 목록 조회", description = "상태(status)로 필터링할 수 있다. 미지정 시 전체 조회.")
    @GetMapping
    public PageResponse<CourseResponse> list(@Parameter(description = "DRAFT/OPEN/CLOSED") @RequestParam(required = false) CourseStatus status,
                                              Pageable pageable) {
        return PageResponse.from(courseService.list(status, pageable));
    }

    @Operation(summary = "강의 상세 조회", description = "현재 신청 인원(enrolledCount), 잔여 좌석(remainingSeats)을 함께 반환한다.")
    @GetMapping("/{courseId}")
    public CourseDetailResponse getDetail(@PathVariable Long courseId) {
        return courseService.getDetail(courseId);
    }

    @Operation(summary = "모집 시작", description = "DRAFT -> OPEN. 개설자 본인만 호출할 수 있다.")
    @PostMapping("/{courseId}/open")
    public CourseResponse open(@PathVariable Long courseId, Authentication authentication) {
        return courseService.open(courseId, authentication.getName());
    }

    @Operation(summary = "모집 마감", description = "OPEN -> CLOSED. 개설자 본인만 호출할 수 있다.")
    @PostMapping("/{courseId}/close")
    public CourseResponse close(@PathVariable Long courseId, Authentication authentication) {
        return courseService.close(courseId, authentication.getName());
    }
}
