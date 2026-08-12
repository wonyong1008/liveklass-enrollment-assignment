package com.liveklass.enrollment.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

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
                .andExpect(jsonPath("$.detail").exists());
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

    private String issueToken(String userId) throws Exception {
        String response = mockMvc.perform(post("/api/auth/token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("userId", userId))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
