package com.sabyeguyz.clinic.sale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** ยกเลิกใบเสร็จต้องมีเหตุผลเสมอ เพื่อให้ตรวจสอบย้อนหลังได้ */
public record SaleVoidRequest(@NotBlank @Size(max = 500) String reason) {}
