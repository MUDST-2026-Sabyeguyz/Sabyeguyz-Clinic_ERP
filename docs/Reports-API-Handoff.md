# Dashboard & Reports — Handoff (Frontend + Data)

API สำหรับหน้า Dashboard และ Reports ตาม Workflow Brief V1 §8
ตรงกับซอร์สบน `develop` หลัง PR #21 บวกบรานช์ `fix/api-paths-to-spec` (8 ก.ย. 2026)

## 0. สถานะปัจจุบัน

- Dashboard/Reports merge เข้า `develop` แล้ว (PR #21) ต่อจาก Payment/Receipt (PR #20)
- **บรานช์ `fix/api-paths-to-spec` ย้าย path ให้ตรง `docs/context.md` §8** — ยังไม่ merge
  รอบแรกผมตั้งชื่อเองโดยไม่ได้เปิด §8 ดู เลยได้ `/api/reports/dashboard` แทน `/api/dashboard/summary`
  และลืมทำ `/api/reports/appointments` ทั้งหมดนี้แก้ในบรานช์นั้นแล้ว
- Backend tests: **219 tests ผ่านทั้งหมด** (`mvn clean test`)
- ตรวจกับ PostgreSQL 16 จริงแล้ว **34/34** (รายงาน) + **28/28** (path ใหม่ + Appointment Report)
- **ไม่มี migration ใหม่** อ่านอย่างเดียว schema ยังอยู่ที่ `v14`
- ยังไม่มีหน้าจอ — backend + API เท่านั้น
  หน้า `ReportsView.vue` ปัจจุบันบนบรานช์ `feature/complete-v1-workflows` เขียนไว้ว่ารายงานพวกนี้
  "จะเปิดเมื่อ transactional schema ได้รับการยืนยัน" → ตอนนี้ยืนยันแล้ว ต่อ API ชุดนี้ได้เลย
- Swagger สด: tag `Dashboard & Reports`

---

## 1. กติกาที่ต้องรู้ก่อนใช้ตัวเลข

**รายได้ไม่ได้มาจาก `sales.total`** — มาจากผลรวมของ `sale_payments.amount` ซึ่งเป็นบวกตอนรับเงิน
และเป็นลบตอนคืนเงิน ใบเสร็จที่ถูกยกเลิกจึงกลายเป็น 0 โดยอัตโนมัติ ทั้งที่ `sales.total` ยังเท่าเดิมอยู่ในตาราง
ถ้าทีมไหนทำกราฟเองแล้ว `SUM(sales.total)` ตัวเลขจะพองทุกครั้งที่มีการยกเลิก

**วันที่เป็นเวลาคลินิก (Asia/Bangkok) ไม่ใช่ UTC** — และนับรวมทั้ง `from` และ `to`
ใบเสร็จที่ออกตอนตี 0 ครึ่งของวันที่ 12 ตามเวลาไทย จะอยู่ในวันที่ 12 ไม่ใช่ 11

**การยกเลิกย้อนหลังกระทบรายงานของเดือนที่ขาย ไม่ใช่เดือนที่ยกเลิก** — รายได้ถูกผูกกับ `sold_at` ของใบเสร็จ
ยกเลิกใบเดือนกันยายนในเดือนตุลาคม ตัวเลขเดือนกันยายนจะลดลง (Assumption — ถ้า PM อยากได้แบบ credit note
ที่กระทบเดือนที่ยกเลิกแทน ต้องเปลี่ยนตรงนี้)

**Visit** = นัดที่ผู้ป่วยมาถึงจริง (`ARRIVED`, `IN_SERVICE`, `COMPLETED`)
นัดที่ยังไม่ถึงเวลา ถูกยกเลิก เลื่อน หรือไม่มาตามนัด **ไม่นับ** (Assumption — brief เขียนแค่ "จำนวน Visits")

**Unique Patients** = ผู้ป่วยไม่ซ้ำที่ *มาจริง* ในช่วงนั้น ไม่ใช่จำนวนผู้ป่วยที่ลงทะเบียนทั้งหมด

---

## 2. API Contract

ทุก endpoint รับ `from`, `to` (`YYYY-MM-DD`, บังคับ, นับรวมทั้งสองด้าน) และ `branchCode` (ไม่บังคับ — ไม่ส่ง = ทุกสาขา)

> **เส้นทางตาม `docs/context.md` §8** — Dashboard อยู่ที่ `/api/dashboard/summary` (ไม่ใช่ `/api/reports/dashboard`)
> ส่วนรายงานอยู่ใต้ `/api/reports/`

### 2.1 Dashboard

```
GET /api/dashboard/summary?from=2026-09-01&to=2026-09-30&branchCode=RP
```

```jsonc
{
  "from": "2026-09-01", "to": "2026-09-30", "branchCode": "RP",
  "uniquePatients": 2,
  "patientsByGender": [ { "gender": "หญิง", "patients": 1 }, { "gender": "ชาย", "patients": 1 } ],
  "visits": 3,
  "totalRevenue": 17400.00,
  "courseRevenue": 15000.00,
  "singleVisitRevenue": 0.00,
  "assessmentRevenue": 2400.00,
  "receiptsIssued": 2,
  "receiptsVoided": 1
}
```

- `courseRevenue + singleVisitRevenue + assessmentRevenue` = `totalRevenue` เสมอ (มีเทสยืนยัน)
- ผลรวม `patientsByGender[].patients` = `uniquePatients` เสมอ
- ผู้ป่วยที่ไม่ได้ระบุเพศจะมารวมกันในกลุ่ม `"ไม่ระบุ"`

### 2.2 Appointment Report

```
GET /api/reports/appointments?from=…&to=…&branchCode=RP
```

```jsonc
{
  "appointments": 4,
  "visits": 2,
  "noShowRate": 0.3333,
  "byStatus":    [ { "status": "COMPLETED", "appointments": 2 }, { "status": "NO_SHOW", "appointments": 1 },
                   { "status": "CONFIRMED", "appointments": 1 } ],
  "byTherapist": [ { "therapistId": 1, "code": "RP-T01", "fullName": "…", "appointments": 4, "visits": 2 } ],
  "daily":       [ { "date": "2026-09-08", "appointments": 0, "visits": 0 },
                   { "date": "2026-09-09", "appointments": 4, "visits": 2 } ]
}
```

- `visits` ใช้นิยามเดียวกับ Dashboard คือนัดที่ผู้ป่วยมาถึงจริง
- **`noShowRate` หารด้วยนัดที่ถึงกำหนดแล้วเท่านั้น** (มาจริง + ไม่มาตามนัด)
  นัดที่ยังไม่ถึงเวลาและนัดที่เลื่อนไม่ถูกนับในตัวหาร ไม่งั้นจองล่วงหน้าเยอะๆ จะทำให้อัตราดูดีเกินจริง
- `byStatus` และ `daily` รวมกันได้เท่า `appointments` เสมอ · `daily` เติมวันที่ไม่มีนัดเป็น 0
- แทนที่วิธีเดิมใน `ReportsView.vue` ที่ไปเรียก `GET /api/appointments` แล้วอ่าน `totalItems`

### 2.3 Revenue Report

```
GET /api/reports/revenue?from=2026-09-01&to=2026-09-30&branchCode=RP
```

```jsonc
{
  "totalRevenue": 17400.00,
  "byCategory": [
    { "type": "ASSESSMENT",   "amount": 2400.00,  "share": 0.1379 },
    { "type": "SINGLE_VISIT", "amount": 0.00,     "share": 0.0000 },
    { "type": "COURSE",       "amount": 15000.00, "share": 0.8621 }
  ],
  "byBranch": [ { "branchCode": "RP", "branchName": "สาขาราชพฤกษ์", "amount": 17400.00 } ],
  "daily": [ { "date": "2026-09-01", "amount": 0.00 }, { "date": "2026-09-02", "amount": 17400.00 } ]
}
```

- `byCategory` คืนครบทุกชนิดเสมอ แม้ยอดเป็น 0 — ตารางฝั่ง Frontend จะได้ไม่กระโดด
- `daily` เติมวันที่ไม่มียอดเป็น 0 ให้แล้ว ยิงมาช่วงไหนก็ได้จำนวนวันเท่านั้นพอดี → เอาไปวาดกราฟได้ตรงๆ
- ผลรวมของ `daily[].amount` = `totalRevenue` เสมอ

### 2.4 Course Balance Report

```
GET /api/reports/course-balances?from=…&to=…&branchCode=RP&patientId=1
```

กรองด้วย **วันที่ซื้อคอร์ส** ไม่ใช่วันที่ใช้สิทธิ์ · `patientId` ไม่บังคับ · `404` ถ้าไม่พบผู้ป่วย

```jsonc
{
  "purchases": 1,
  "totalPurchasedSessions": 13, "totalUsedSessions": 1, "totalRemainingSessions": 12,
  "totalValue": 15000.00,
  "rows": [ {
    "coursePurchaseId": 1, "patientId": 3, "hn": "26010900003", "patientFullName": "…",
    "branchCode": "RP", "courseCode": "PKG01", "courseName": "คอร์สกายภาพ 10 ครั้ง",
    "price": 15000.00, "purchased": 13, "used": 1, "remaining": 12,
    "purchasedAt": "…", "expiresAt": "…", "expired": false, "status": "ACTIVE"
  } ]
}
```

- `purchased = used + remaining` เสมอสำหรับคอร์สที่ยัง `ACTIVE`
- คอร์สที่ถูกยกเลิกยังแสดงในตาราง (ครั้งที่ใช้ไปแล้วยังอยู่) แต่**ไม่ถูกนับใน `totalValue`**
- ยังไม่มีคอลัมน์ "โอน" ที่ brief ขอ เพราะ Course Transfer ยังไม่เข้า `develop` (ดูข้อ 4)

### 2.5 Staff Sales Report

```
GET /api/reports/staff-sales?from=…&to=…&branchCode=RP
```

```jsonc
{
  "branchTotal": 17400.00,
  "receipts": 2,
  "rows": [ { "staffId": 2, "employeeCode": "SP01", "fullName": "…", "receipts": 1, "amount": 15000.00 } ],
  "unassigned": { "staffId": null, "employeeCode": null, "fullName": "ไม่ระบุผู้ขาย", "receipts": 1, "amount": 2400.00 }
}
```

- `rows` เรียงจากยอดมากไปน้อย
- ใบเสร็จที่ไม่ได้ระบุผู้ขาย**ไม่ถูกทิ้ง** แต่ไปอยู่ใน `unassigned`
  ผลรวม `rows` + `unassigned` = `branchTotal` เสมอ (มีเทสยืนยัน)
- ไม่นับใบที่ถูกยกเลิก

---

## 3. ข้อควรระวังตอนต่อ Frontend

- ตัวเลขเงินทุกตัวเป็น **ทศนิยม 2 ตำแหน่งเสมอ** ไม่มี null ถ้าไม่มีข้อมูลจะเป็น `0.00`
- `share` เป็นทศนิยม 4 ตำแหน่ง (`0.8621` = 86.21%) ไม่ใช่เปอร์เซ็นต์
- ช่วงเวลาสูงสุด 2 ปี เกินกว่านั้น `400`
- ยังไม่มี auth บน endpoint พวกนี้ — เมื่อ Spring Security เข้า `develop` ต้องจำกัดสิทธิ์
  (รายงานรายได้และยอดขายรายบุคคลไม่ควรเปิดให้ทุก role เห็น)

## 4. Reports ที่ brief ขอแต่ยังทำไม่ได้

| Report | ทำไมยัง |
|---|---|
| **Course Transfer Report** | Course Transfer อยู่ในบรานช์ `feature/complete-v1-workflows` ที่ยังไม่ merge · พอ merge แล้วเพิ่มคอลัมน์ "โอนเข้า/โอนออก" ใน Course Balance Report และทำรายงานแยกได้ทันที เพราะ ledger รองรับอยู่แล้ว |
| **Commission Report** | ยังไม่มี Commission Rule entity เลยในระบบ · `sales.salesperson_staff_id` และ `treatments.staff_id` เก็บครบแล้ว เหลือกติกาการคำนวณจาก PM (ไฟล์ `Commission_System_Requirement_Dev.pdf` ยังไม่ได้อ่าน) |

## 5. Open Question สำหรับ PM

1. **Visit** ควรนับ `ARRIVED` ที่ยังไม่ได้รับบริการด้วยไหม หรือเอาเฉพาะ `COMPLETED`
2. **ยกเลิกย้อนเดือน** ควรลดยอดของเดือนที่ขาย (ตอนนี้ทำแบบนี้) หรือบันทึกเป็นรายการลบในเดือนที่ยกเลิก
3. **Unique Patients** ควรเป็นผู้ป่วยที่มาจริง (ตอนนี้ทำแบบนี้) หรือผู้ป่วยที่ลงทะเบียนใหม่ในช่วงนั้น
