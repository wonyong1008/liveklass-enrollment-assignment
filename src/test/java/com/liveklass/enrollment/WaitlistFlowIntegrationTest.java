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
 * 정원이 가득 찬 강의 -> 대기 등록 -> 좌석 보유자가 취소하면 대기 1순위가 자동으로
 * PENDING으로 승급되는지 실제 API 호출로 검증하는 골든패스 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WaitlistFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String issueToken(String userId) throws Exception {
        String response = mockMvc.perform(post("/api/auth/token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("userId", userId))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    @Test
    void 정원마감_대기등록_후_취소되면_대기1순위가_자동승급된다() throws Exception {
        String creatorToken = issueToken("waitlist-creator");
        String studentAToken = issueToken("waitlist-student-a");
        String studentBToken = issueToken("waitlist-student-b");

        Map<String, Object> createRequest = Map.of(
                "title", "정원 1명 강의",
                "description", "대기열 테스트",
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

        String enrollResponse = mockMvc.perform(withAuth(post("/api/enrollments"), studentAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("courseId", courseId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long studentAEnrollmentId = objectMapper.readTree(enrollResponse).get("id").asLong();

        // 정원이 이미 찼으므로 일반 신청은 거부되어야 한다 (필수 요구사항 유지 확인)
        mockMvc.perform(withAuth(post("/api/enrollments"), studentBToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("courseId", courseId))))
                .andExpect(status().isConflict());

        String waitlistResponse = mockMvc.perform(withAuth(post("/api/courses/" + courseId + "/waitlist"), studentBToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITLISTED"))
                .andReturn().getResponse().getContentAsString();
        Long studentBEnrollmentId = objectMapper.readTree(waitlistResponse).get("id").asLong();

        mockMvc.perform(withAuth(get("/api/courses/" + courseId), creatorToken))
                .andExpect(jsonPath("$.enrolledCount").value(1))
                .andExpect(jsonPath("$.remainingSeats").value(0))
                .andExpect(jsonPath("$.waitlistCount").value(1));

        // student-a가 취소하면 좌석이 하나 비고, student-b가 자동으로 PENDING 승급되어야 한다
        mockMvc.perform(withAuth(post("/api/enrollments/" + studentAEnrollmentId + "/cancel"), studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(withAuth(get("/api/courses/" + courseId), creatorToken))
                .andExpect(jsonPath("$.enrolledCount").value(1))
                .andExpect(jsonPath("$.waitlistCount").value(0));

        mockMvc.perform(withAuth(get("/api/enrollments/me"), studentBToken))
                .andExpect(jsonPath("$.content[0].id").value(studentBEnrollmentId))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }
}
