package com.liveklass.enrollment.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 컨트롤러 응답에서 Spring Data의 {@link Page}를 그대로 노출하면 pageable, sort 등
 * 내부 구현 세부사항이 API 계약에 섞여 들어간다. 클라이언트에 필요한 필드만 추려 감싼다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
