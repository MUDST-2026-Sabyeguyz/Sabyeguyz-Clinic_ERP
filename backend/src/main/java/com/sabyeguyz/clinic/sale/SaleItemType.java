package com.sabyeguyz.clinic.sale;

/** สินค้าที่ V1 ขายได้: ตรวจประเมิน, รักษารายครั้ง, และคอร์ส */
public enum SaleItemType {
    ASSESSMENT,
    SINGLE_VISIT,
    COURSE;

    public boolean isService() {
        return this != COURSE;
    }
}
