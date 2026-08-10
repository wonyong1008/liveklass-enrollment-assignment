package com.liveklass.enrollment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인(토큰 발급) -> 강의 개설 -> 모집 시작 -> 수강 신청 -> 결제 확정 -> 취소까지
 * 실제 API를 순서대로 호출해 계층 간 배선(시큐리티-컨트롤러-서비스-예외처리)이
 * 올바른지 확인하는 골든패스 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnrollmentFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String issueToken(String userId) throws Exception {
        String response = mockMvc.perform(post("/api/auth/token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("userId", userId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    @Test
    void 토큰_없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/enrollments/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 강의_개설부터_수강신청_취소까지_전체_흐름이_동작한다() throws Exception {
        String creatorToken = issueToken("creator-flow");
        String studentToken = issueToken("student-flow");

        Map<String, Object> createRequest = Map.of(
                "title", "실전 스프링 부트",
                "description", "백엔드 실무 강의",
                "price", BigDecimal.valueOf(50_000),
                "capacity", 1,
                "startDate", LocalDate.now().toString(),
                "endDate", LocalDate.now().plusDays(30).toString()
        );

        String createResponse = mockMvc.perform(withAuth(post("/api/courses"), creatorToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        Long courseId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(withAuth(post("/api/courses/" + courseId + "/open"), creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        String enrollResponse = mockMvc.perform(withAuth(post("/api/enrollments"), studentToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("courseId", courseId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        Long enrollmentId = objectMapper.readTree(enrollResponse).get("id").asLong();

        mockMvc.perform(withAuth(get("/api/courses/" + courseId), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolledCount").value(1))
                .andExpect(jsonPath("$.remainingSeats").value(0));

        mockMvc.perform(withAuth(post("/api/enrollments/" + enrollmentId + "/confirm"), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(withAuth(post("/api/enrollments/" + enrollmentId + "/cancel"), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(withAuth(get("/api/courses/" + courseId), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolledCount").value(0))
                .andExpect(jsonPath("$.remainingSeats").value(1));
    }

    @Test
    void 정원이_찬_강의는_신청시_409를_반환한다() throws Exception {
        String creatorToken = issueToken("creator-full");
        String studentAToken = issueToken("student-a");
        String studentBToken = issueToken("student-b");

        Map<String, Object> createRequest = Map.of(
                "title", "정원 1명 강의",
                "description", "설명",
                "price", BigDecimal.valueOf(10_000),
                "capacity", 1,
                "startDate", LocalDate.now().toString(),
                "endDate", LocalDate.now().plusDays(30).toString()
        );

        String createResponse = mockMvc.perform(withAuth(post("/api/courses"), creatorToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();
        Long courseId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(withAuth(post("/api/courses/" + courseId + "/open"), creatorToken));

        mockMvc.perform(withAuth(post("/api/enrollments"), studentAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("courseId", courseId))))
                .andExpect(status().isCreated());

        mockMvc.perform(withAuth(post("/api/enrollments"), studentBToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("courseId", courseId))))
                .andExpect(status().isConflict());
    }
}
