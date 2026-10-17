package com.sabyeguyz.clinic.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CoursePurchaseRequest(
        @NotNull Long patientId,
        @NotNull Long courseId,
        @NotBlank @Size(max = 2) String branchCode, Long sellerEmployeeId
) { public CoursePurchaseRequest(Long patientId,Long courseId,String branchCode){this(patientId,courseId,branchCode,null);} }
