package com.liveklass.enrollment.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BusinessException 계열 말고도, 경로변수 타입이 안 맞는 경우처럼 흔한 클라이언트 실수도
 * 다른 에러와 동일한 ProblemDetail 포맷으로 응답하는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 경로변수_타입이_맞지_않으면_400_ProblemDetail로_응답한다() throws Exception {
        String token = issueToken("type-mismatch-user");

        mockMvc.perform(get("/api/courses/not-a-number").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/courses/not-a-number"));
    }

    @Test
    void 요청_본문이_깨진_JSON이면_500이_아니라_400으로_응답한다() throws Exception {
        String token = issueToken("malformed-body-user");

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{ 이거 json 아님"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void userId가_DB_컬럼_길이를_넘으면_토큰발급_단계에서_400으로_거부된다() throws Exception {
        String tooLongUserId = "u".repeat(65);

        mockMvc.perform(post("/api/auth/token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("userId", tooLongUserId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void 강의_제목이_DB_컬럼_길이를_넘으면_500_대신_400으로_응답한다() throws Exception {
        String token = issueToken("long-title-user");
        String tooLongTitle = "제".repeat(201);

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", tooLongTitle,
                                "description", "d",
                                "price", 10000,
                                "capacity", 10,
                                "startDate", "2026-09-01",
                                "endDate", "2026-10-01"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void 강의_설명이_너무_길면_500_대신_400으로_응답한다() throws Exception {
        String token = issueToken("long-description-user");
        String tooLongDescription = "d".repeat(5001);

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "제목",
                                "description", tooLongDescription,
                                "price", 10000,
                                "capacity", 10,
                                "startDate", "2026-09-01",
                                "endDate", "2026-10-01"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * @Size/@Digits로 필드별 검증을 해왔지만, 앞으로 누락될 수도 있는 안전망이
     * 실제로 500 대신 400을 내려주는지 핸들러를 직접 호출해 확인한다(검증을 다 통과하는
     * 요청으로는 이제 DB 제약 위반을 자연스럽게 재현하기 어렵기 때문).
     */
    @Test
    void db_제약조건_위반은_500_대신_400_안전망으로_처리된다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/courses");

        var problem = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("Data too long for column 'title'"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getInstance()).isEqualTo(java.net.URI.create("/api/courses"));
    }

    /**
     * apply()/cancel()의 비관적 락이 실제 부하 상황에서 대기 타임아웃/데드락으로 실패하면
     * 500이 아니라 재시도 가능함을 알리는 409여야 한다. 실제 락 경합을 안정적으로 재현하기
     * 어려워 핸들러를 직접 호출해 확인한다.
     */
    @Test
    void 락_경합_실패는_500_대신_409로_처리된다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/enrollments");

        var problem = handler.handleLockingFailure(
                new org.springframework.dao.CannotAcquireLockException("lock wait timeout"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getInstance()).isEqualTo(java.net.URI.create("/api/enrollments"));
    }

    private String issueToken(String userId) throws Exception {
        String response = mockMvc.perform(post("/api/auth/token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("userId", userId))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
