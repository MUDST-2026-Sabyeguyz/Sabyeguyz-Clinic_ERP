package com.sabyeguyz.clinic.patient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PatientOpenApiTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean PatientRegistrationService registrationService;
    @MockitoBean PatientQueryService queryService;

    @Test
    void exposesSwaggerUiAndCompletePatientOpenApiContract() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/patients'].post.tags[0]").value("Patients"))
                .andExpect(jsonPath("$.paths['/api/patients'].get.tags[0]").value("Patients"))
                .andExpect(jsonPath("$.paths['/api/patients/{id}'].get.tags[0]").value("Patients"))
                .andExpect(jsonPath("$.paths['/api/patients'].get.parameters.length()").value(4))
                .andExpect(jsonPath("$.paths['/api/patients'].get.responses['400'].content['application/json'].schema").exists())
                .andExpect(jsonPath("$.paths['/api/patients/{id}'].get.responses['404'].content['application/json'].schema").exists())
                .andExpect(jsonPath("$.components.schemas.PatientRegistrationRequest").exists())
                .andExpect(jsonPath("$.components.schemas.PatientSearchResponse").exists())
                .andExpect(jsonPath("$.components.schemas.PatientProfileResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse").exists());
    }
}
