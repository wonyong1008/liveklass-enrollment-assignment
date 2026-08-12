package com.liveklass.enrollment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("인증 토큰이 없거나 유효하지 않습니다."));
    }

    @Test
    void 강의_개설부터_수강신청_취소까지_전체_흐름이_동작한다() throws Exception {
        String creatorToken = issueToken("creator-flow");
        String studentToken = issueToken("student-flow");

        Map<String, Object> createRequest = Map.of(
                "title", "실전 스프링 부트",
                "description", "백엔드 실무 강의",
                "price", 50_000L,
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
                "price", 10_000L,
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

    /**
     * DRAFT(미공개) 강의는 개설자 본인만 목록/상세에서 볼 수 있어야 한다. 다른 사람에게는
     * 존재 자체를 숨기기 위해 403이 아니라 404를 준다.
     */
    @Test
    void DRAFT_강의는_개설자_본인만_조회할_수_있다() throws Exception {
        String creatorToken = issueToken("draft-owner");
        String otherToken = issueToken("draft-stranger");

        Map<String, Object> createRequest = Map.of(
                "title", "아직 준비중인 강의",
                "description", "d",
                "price", 10_000L,
                "capacity", 5,
                "startDate", LocalDate.now().toString(),
                "endDate", LocalDate.now().plusDays(30).toString()
        );

        String createResponse = mockMvc.perform(withAuth(post("/api/courses"), creatorToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long courseId = objectMapper.readTree(createResponse).get("id").asLong();

        // 개설자 본인은 상세조회 가능
        mockMvc.perform(withAuth(get("/api/courses/" + courseId), creatorToken))
                .andExpect(status().isOk());

        // 다른 사람에게는 404 (존재 자체를 숨김)
        mockMvc.perform(withAuth(get("/api/courses/" + courseId), otherToken))
                .andExpect(status().isNotFound());

        // status=DRAFT로 목록을 조회해도 다른 사람에게는 안 보임
        mockMvc.perform(withAuth(get("/api/courses?status=DRAFT"), otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + courseId + ")]").doesNotExist());

        // 개설자 본인의 DRAFT 필터 목록에는 보임
        mockMvc.perform(withAuth(get("/api/courses?status=DRAFT"), creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + courseId + ")]").exists());

        // 상태 미지정 전체 조회에도 다른 사람의 DRAFT는 섞이지 않음
        mockMvc.perform(withAuth(get("/api/courses"), otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + courseId + ")]").doesNotExist());
    }
}
