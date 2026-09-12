package com.sabyeguyz.clinic.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PatientQueryApiTest {
    @Autowired PatientRegistrationService registrationService;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private PatientResponse somchai;

    @BeforeEach
    void createPatients() {
        somchai = register("query-somchai", "query-somchai@example.com", "1100000000101", "สมชาย ใจดี", "0811111111");
        register("query-somsri", "query-somsri@example.com", "1100000000102", "สมศรี สุขใจ", "0822222222");
    }

    @Test
    void searchesByHn() throws Exception {
        assertSingleResult(somchai.hn(), "สมชาย ใจดี");
    }

    @Test
    void searchesByName() throws Exception {
        assertSingleResult("ชาย ใจ", "สมชาย ใจดี");
    }

    @Test
    void searchesByPhone() throws Exception {
        assertSingleResult("081111", "สมชาย ใจดี");
    }

    @Test
    void searchesByIdentityNumber() throws Exception {
        assertSingleResult("00000000101", "สมชาย ใจดี");
    }

    @Test
    void returnsEmptyPageWhenNoResult() throws Exception {
        mockMvc.perform(get("/api/patients").param("query", "does-not-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void paginatesInDatabase() throws Exception {
        mockMvc.perform(get("/api/patients").param("query", "สม").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/patients").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        mockMvc.perform(get("/api/patients").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void returnsExistingPatientProfileWithoutPasswordHash() throws Exception {
        String content = mockMvc.perform(get("/api/patients/{id}", somchai.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(somchai.id()))
                .andExpect(jsonPath("$.branch.code").value("RP"))
                .andReturn().getResponse().getContentAsString();

        JsonNode profile = objectMapper.readTree(content);
        assertThat(profile.has("password")).isFalse();
        assertThat(profile.has("passwordHash")).isFalse();
        assertThat(profile.has("username")).isFalse();
        assertThat(profile.has("email")).isTrue();
    }

    @Test
    void returnsNotFoundForMissingPatient() throws Exception {
        mockMvc.perform(get("/api/patients/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ไม่พบผู้ป่วย id 999999"));
    }

    private void assertSingleResult(String query, String expectedName) throws Exception {
        mockMvc.perform(get("/api/patients").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].fullName").value(expectedName));
    }

    private PatientResponse register(String username, String email, String identityNumber, String name, String phone) {
        return registrationService.register(new PatientRegistrationRequest(
                "RP", username, email, "password123", "นาย", name, LocalDate.of(1990, 1, 1), "ชาย", phone,
                "NATIONAL_ID", identityNumber, "กรุงเทพฯ", false, false, "", "ผู้ติดต่อ", "ญาติ",
                "0899999999", true));
    }
}
