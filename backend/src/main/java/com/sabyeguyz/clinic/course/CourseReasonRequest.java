package com.sabyeguyz.clinic.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** ทุกการกลับรายการต้องมีเหตุผลเสมอ เพื่อให้ตรวจสอบย้อนหลังได้ */
public record CourseReasonRequest(
        @NotBlank @Size(max = 500) String reason
) {}
