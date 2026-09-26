package com.sabyeguyz.clinic.patient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PatientController.class)
@AutoConfigureMockMvc(addFilters = false)
class PatientControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean PatientRegistrationService registrationService;
    @MockitoBean PatientQueryService queryService;

    @Test
    void registersPatient() throws Exception {
        when(registrationService.register(any())).thenReturn(new PatientResponse(1L, "2607120001", "สมชาย ใจดี", "0812345678", true));
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hn").value("2607120001"));
    }

    @Test
    void rejectsMissingConsent() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("\"privacyConsent\":true", "\"privacyConsent\":false")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.privacyConsent").exists());
    }

    private String validRequest() {
        return """
                {"branchCode":"01","username":"somchai","email":"somchai@example.com","password":"password123",
                "title":"นาย","fullName":"สมชาย ใจดี","birthDate":"1990-01-01","gender":"ชาย","phone":"0812345678",
                "identityDocumentType":"NATIONAL_ID","identityNumber":"1100000000001","address":"กรุงเทพฯ",
                "drugAllergy":false,"foodAllergy":false,"chronicDisease":"",
                "emergencyContactName":"สมหญิง ใจดี","emergencyContactRelationship":"พี่สาว","emergencyContactPhone":"0899999999","privacyConsent":true}
                """;
    }
}
