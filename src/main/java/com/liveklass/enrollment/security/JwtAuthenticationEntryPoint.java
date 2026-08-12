package com.liveklass.enrollment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 시큐리티 필터 체인 단계에서 인증에 실패하면 컨트롤러까지 도달하지 못해
 * GlobalExceptionHandler를 타지 않는다. 응답 포맷을 맞추기 위해 여기서 직접 처리한다.
 * 이 경로는 Spring MVC의 HttpMessageConverter(평소엔 자동으로 UTF-8을 붙여줌)를 거치지
 * 않고 response.getWriter()로 직접 쓰기 때문에, setCharacterEncoding을 명시하지 않으면
 * 서블릿 컨테이너 기본값인 ISO-8859-1로 인코딩되어 한글 detail 메시지가 깨진다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "인증 토큰이 없거나 유효하지 않습니다.");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
