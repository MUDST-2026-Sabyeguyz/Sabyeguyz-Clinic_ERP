package com.sabyeguyz.clinicv2.patient;

import java.util.Map;
import java.util.LinkedHashMap;
import com.sabyeguyz.clinic.cypress.V2RepositoryStub;

import com.sabyeguyz.clinicv2.branch.Branch;
import java.time.LocalDate;
import java.util.Optional;

public class HnGeneratorServiceScenarios {
    V2RepositoryStub<HnSequenceRepository> sequencesStub = new V2RepositoryStub<>(HnSequenceRepository.class);
    HnSequenceRepository sequences = sequencesStub.proxy();

    public Map<String, Object> formatsYearBranchMonthAndRunningNumber() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hn", HnGeneratorService.format(LocalDate.of(2026, 12, 1), "01", 1));
        return out;
    }

    public Map<String, Object> startsANewSequenceAtOne() {
        Map<String, Object> out = new LinkedHashMap<>();
        Branch branch = new Branch();
        branch.setBranchCode("01");
        sequencesStub.on("findById", args -> Optional.empty());

        String hn = new HnGeneratorService(sequences).next(branch, LocalDate.of(2026, 12, 1));

        out.put("hn", hn);
        return out;
    }

    public Map<String, Object> incrementsAnExistingSequence() {
        Map<String, Object> out = new LinkedHashMap<>();
        Branch branch = new Branch();
        branch.setBranchCode("01");
        HnSequence existing = new HnSequence(branch, "2612", 4, java.time.Instant.now(), java.time.Instant.now());
        sequencesStub.on("findById", args -> Optional.of(existing));

        String hn = new HnGeneratorService(sequences).next(branch, LocalDate.of(2026, 12, 1));

        out.put("hn", hn);
        return out;
    }

    public Map<String, Object> rejectsABranchWithoutACode() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.putAll(V2RepositoryStub.outcome(() -> { new HnGeneratorService(sequences).next(new Branch(), LocalDate.now()); return null; }));
        return out;
    }
}
