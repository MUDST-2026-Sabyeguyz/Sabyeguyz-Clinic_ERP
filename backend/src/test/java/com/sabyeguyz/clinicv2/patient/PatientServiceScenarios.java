package com.sabyeguyz.clinicv2.patient;

import java.util.Map;
import java.util.LinkedHashMap;
import com.sabyeguyz.clinic.cypress.V2RepositoryStub;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.CustomerType;
import com.sabyeguyz.clinicv2.enums.Gender;

public class PatientServiceScenarios {
    V2RepositoryStub<PatientRepository> patientsStub = new V2RepositoryStub<>(PatientRepository.class);
    PatientRepository patients = patientsStub.proxy();
    HnGeneratorService hnGenerator = new HnGeneratorService(null) {
        @Override public String next(Branch branch, java.time.LocalDate date) { return "2601120001"; }
    };

    private PatientRegistration validRegistration(Branch branch) {
        return new PatientRegistration(branch, CustomerType.Thai, "นาย", "สมชาย", "ใจดี", Gender.Male,
                "0812345678", null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public Map<String, Object> registersAPatientAndAssignsAnHn() {
        Map<String, Object> out = new LinkedHashMap<>();
        Branch branch = new Branch();

        Patient patient = new PatientService(patients, hnGenerator).register(validRegistration(branch), 1L);

        out.put("patientHn", patient.getPatientHn());
        out.put("patientFname", patient.getPatientFname());
        out.put("registeredBy", patient.getRegisteredBy());
        return out;
    }

    public Map<String, Object> rejectsMissingBranch() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.putAll(V2RepositoryStub.outcome(() -> { new PatientService(patients, hnGenerator).register(
                validRegistration(null), 1L); return null; }));
        return out;
    }

    public Map<String, Object> rejectsMissingRequiredField() {
        Map<String, Object> out = new LinkedHashMap<>();
        Branch branch = new Branch();
        PatientRegistration missingPhone = new PatientRegistration(branch, CustomerType.Thai, "นาย", "สมชาย",
                "ใจดี", Gender.Male, "  ", null, null, null, null, null, null, null, null, null, null, null, null);

        out.putAll(V2RepositoryStub.outcome(() -> { new PatientService(patients, hnGenerator).register(missingPhone, 1L); return null; }));
        return out;
    }
}
