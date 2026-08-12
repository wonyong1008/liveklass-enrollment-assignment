package com.liveklass.enrollment.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return problem(ex.getStatus(), ex.getMessage(), request);
    }

    /**
     * getFieldErrors()만 읽으면 클래스 레벨(cross-field) 검증 오류를 놓친다. 지금은 필드 단위
     * 제약만 쓰고 있지만, 나중에 시작일<=종료일 같은 크로스필드 검증을 추가하면 그 위반은
     * getGlobalErrors()에만 담긴다 — getAllErrors()로 둘 다 커버한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult().getAllErrors().stream()
                .map(this::describeError)
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, detail, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String detail = "파라미터 '%s'의 값 '%s'이(가) 올바르지 않습니다.".formatted(ex.getName(), ex.getValue());
        return problem(HttpStatus.BAD_REQUEST, detail, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * 요청 본문이 비어있거나 JSON 형식이 깨진 경우. 아래 catch-all(Exception.class)이
     * 이 예외까지 가로채면 스프링이 원래 400으로 처리하던 걸 500으로 만들어버리므로,
     * catch-all보다 구체적인 이 핸들러를 먼저 둔다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해주세요.", request);
    }

    /**
     * DTO의 @Size/@Digits 등으로 필드별 길이·범위 검증을 해왔지만(title/description/price 등),
     * 새 필드를 추가하면서 매칭되는 DB 컬럼 제약을 놓치는 실수가 실제로 세 번 반복됐다
     * (.notes/버그수정기록.md 참고). 개별 필드 검증은 계속 유지하되, 혹시 놓친 경우를 대비한
     * 안전망으로 DB 제약 위반도 500이 아니라 400으로 응답한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("DB 제약조건 위반: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.BAD_REQUEST, "요청한 값이 허용 범위를 벗어났습니다.", request);
    }

    /**
     * apply/cancel처럼 Course·Enrollment row에 비관적 락(FOR UPDATE)을 거는 경로에서, 락 경합이
     * 심해 InnoDB의 innodb_lock_wait_timeout을 넘기거나 데드락으로 걸리면 이 예외가 발생한다.
     * 서버 결함이 아니라 "지금 몰려서 못 처리한" 일시적 상황이므로 500이 아니라 재시도를
     * 유도하는 409로 응답한다.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handleLockingFailure(PessimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("락 획득 실패(경합 또는 데드락): {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.CONFLICT, "요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해주세요.", request);
    }

    /**
     * 위에서 처리되지 않은 나머지 예외도 다른 4xx/5xx 응답과 동일한 ProblemDetail 포맷으로
     * 내려간다. 원인 파악을 위해 서버 로그에는 스택트레이스를 남기되, 클라이언트에는 내부
     * 구현 정보를 노출하지 않는다.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("처리되지 않은 예외 발생: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", request);
    }

    private String describeError(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        return error.getDefaultMessage();
    }

    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
