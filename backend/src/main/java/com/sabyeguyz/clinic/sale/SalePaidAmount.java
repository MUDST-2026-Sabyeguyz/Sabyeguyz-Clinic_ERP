package com.sabyeguyz.clinic.sale;

import java.math.BigDecimal;

/** ยอดเงินที่รับจริงของใบเสร็จหนึ่งใบ ใช้ดึงทีละหลายใบเพื่อไม่ให้เกิด query ต่อหนึ่งแถวตอนลิสต์ */
public record SalePaidAmount(Long saleId, BigDecimal amount) {}
