# P2: Patient fields และ exact identity lookup

วันที่: 14 ตุลาคม 2026 (Asia/Bangkok)

Branch: `codex/non-commission-backend`, parent `0285afd` (P1 appointment authorization)

สถานะ: ผ่าน gates ของ P2 ส่วนที่ไม่ติดคำถาม พร้อม commit; Q1/Q2/Q10 และ hardware integration ยังเปิดอยู่

## Scope และ source

Confirmed Requirement: เริ่ม P2 ตามแผน โดยไม่เปลี่ยน commission และตรวจ requirements/tests ก่อน commit

ตรวจ `C:/Users/napal/Downloads/Requirement + Feedback Update.pdf` หน้า 1 ทั้งข้อความและ rendered page ใหม่: R5 reader integration, R6 คำนำหน้า 5 กลุ่ม, R7 เพศ 3 ค่า, R8 ชื่อกลาง, R9 ต่างชาติไม่บังคับ phone/birthDate/passport, R10 address optional และ phone policy ที่ยังขัดกัน, R11 duplicate-by-name ที่ยังต้องตอบ Q2

ใช้ scope/plan ที่บันทึกไว้ใน `docs/superpowers/specs/2026-10-14-non-commission-scope.md` และ `docs/superpowers/plans/2026-10-14-non-commission-implementation.md`

ส่วนที่รับเข้า release: middleName/structured names, titles/genders, exact national-ID lookup, frontend register/profile และ address optional ส่วน Q1/Q2/Q10 และเครื่องอ่านบัตรจริงยังไม่ปิด ไม่ถือว่าทำ P2 รวมทุก backlog ครบแล้ว

## Implementation และ contract

- Map `Patient.patientMiddleName` → คอลัมน์ `patient_middle_name VARCHAR(100)` ที่มีอยู่แล้ว; ไม่มี migration ใหม่และไม่แก้ V1–V3
- Register/update/profile เดินค่า middleName ครบ; trim/blank→null และตรวจความยาว 100; update omitted รักษาค่าเดิม, null/blank ล้างค่า, object/number ได้ 400
- Request ใช้ JsonNode เฉพาะ middleName เพื่อแยก omitted จาก explicit null; service ยังคงใช้ typed String/flag ไม่เขียน arbitrary JSON ลง DB
- Profile เพิ่ม firstName/lastName/middleName; patient summary/profile fullName รวมชื่อกลาง แต่ไม่ย้ายชื่อกลางเข้า firstName; UI edit ใช้ structured fields เพื่อไม่ทำชื่อหลายคำเสีย
- Title aliases Mr./Miss/Mrs./Boy/Girl บันทึก canonical นาย/นางสาว/นาง/เด็กชาย/เด็กหญิง สำหรับรายการ create/update ใหม่; ไม่ normalize/backfill แถวเก่าโดยอัตโนมัติ
- UI title 5 กลุ่มแสดงสองภาษา; gender Male/Female/NotSpecified แสดง ชาย/หญิง/ไม่ระบุ; title/gender เลือกแยกกันเพื่อไม่ overwrite ค่าที่ผู้ใช้เลือก ส่วน Other เดิมยังรองรับ
- `GET /api/patients/identity-lookup?nationalId=<13 ASCII digits>` ตรวจ authenticated active user และ equality กับ national_id เท่านั้น; ไม่ค้น passport/phone/name และไม่ใช้ wildcard/partial match
- Lookup ข้ามสาขาตาม global patient directory; คืนเพียง exists กับ patient{id,hn,fullName} หรือ patient:null ไม่ส่ง national ID, phone, DOB, address กลับ
- UI ใช้ `patientApi.identityLookup` แทน broad identity search; ไม่มี hardware daemon/การอ่านบัตรจริงใน JVM และยังไม่ได้ทดสอบกับเครื่องอ่านบัตร
- customerType ของ UI ส่ง Thai/Foreigner แยกจาก customer group; address ไม่บังคับแล้ว ส่วนกฎ phone ไทยคงเดิมระหว่าง Q1; foreigner optional fields ใช้พฤติกรรม backend เดิม
- Profile placeholder allergy/consent/emergency ยังไม่ใช่ persisted data; T27 รอ Q10 และไม่เพิ่ม fake persistence ในงานนี้

## RED → GREEN และ code review

1. API RED แรก: 13 tests, ผ่าน 3 / ล้ม 10, exit 1; ไม่มี middleName/structured profile, English title ไม่ canonical, ไม่ระบุถูก map เป็น Other, exact lookup ยังไม่มี route
2. Frontend RED: 5 tests, ผ่าน 3 / ล้ม 2 เพราะ `patientApi.identityLookup` ยังไม่มี
3. API หลัง implementation ผ่าน 12/13; ข้อที่ล้มเป็น Cypress `.its(...).should('equal', null)` ซึ่งไม่รองรับ null subject แบบนี้ จึงเปลี่ยนเป็น `should('be.null')` โดยไม่แก้ product เพื่อหลบ failure; full suite หลังแก้ผ่าน 174/174
4. Read-only code review พบ legacy GET→PUT ที่ส่ง fullName รวมชื่อกลาง อาจ split ซ้ำจน lastName มีชื่อกลางอีกครั้ง; เพิ่ม regression และเห็น RED 14 tests: ผ่าน 13 / ล้ม 1 ก่อนแก้
5. Review เพิ่มพบ registration ส่ง fullName+middleName โดยไม่ส่ง structured names มีความกำกวมเช่นกัน; เพิ่ม regression และเห็น RED 15 tests: ผ่าน 14 / ล้ม 1 ก่อนแก้
6. แก้ให้ legacy fullName เดิมคงองค์ประกอบชื่อ; changed fullName ที่กำกวมต้องส่ง structured names; register nonblank middleName ต้องระบุ firstName/lastName; ตรวจทั้ง omitted/null/blank firstName fallback ไม่ให้หลบ guard
7. Read-only scoped re-review ไม่พบ actionable finding เพิ่มหลังแก้ทั้งสองจุด; ผล runtime สุดท้ายบันทึกในตารางด้านล่าง

ข้อจำกัด compatibility ที่ตั้งใจ: client เก่าที่แก้ชื่อผู้ป่วยซึ่งมี middleName ด้วย fullName ก้อนเดียวจะได้ 400 เมื่อกำกวม ต้องส่ง firstName/lastName แยก; ไม่เดาวิธีแยก compound names แล้วเสี่ยงแก้ข้อมูลผิด

## ผล verification

PostgreSQL 16.15 ใน container `alice-noncommission-p2-20261014`, owner label `codex.task=noncommission-p2-20261014`, bind `127.0.0.1:55434`, database `noncommission_p2` สร้างใหม่ของงาน; password สุ่มอยู่ใน process environment ไม่อยู่ใน Git; API/browser tests รันตามลำดับไม่ให้ fixture reset ชนกัน

| Check | ผลจริง |
|---|---|
| Backend full API v2 รอบป้องกัน ambiguity ทั้ง register/update | **176/176 จาก 20 specs**, failed/pending/skipped 0, exit 0; รวม P2 15/15, P1 18/18 และ commission 12+5 cases; หยุด server 14:57:15 +07:00 |
| Backend full API + package ของโค้ดสุดท้าย รวม blank/partial-name fallback regression | **176/176 จาก 20 specs**, failed/pending/skipped 0, exit 0; ตรวจซ้ำ JUnit XML ได้ 176 tests, 0 failures/skipped; runner หยุด server 15:04:27 +07:00 และ package BUILD SUCCESS 15:04:31 +07:00 |
| Backend clean package + dependency classpath | BUILD SUCCESS, exit 0; Java release 17, main 344/test 24 files; จบ 14:57:39 +07:00 |
| SchemaCheck บน DB P2 | **490 mapped columns, 0 problems**, Hibernate validate 45 entities; wiring 21 controllers / 30 services; SCHEMA_CHECK_OK, exit 0 |
| Frontend unit | **30/30 จาก 5 files**, exit 0 รวม adapter tests ใหม่ 2 ข้อ |
| Frontend type-check/build | vue-tsc และ Vite production build ผ่าน, exit 0 |
| Browser ผ่าน backend/PostgreSQL จริง | **26/26**, exit 0: patients 23/23 และ appointment branch isolation 3/3; test servers หยุด 14:59:17 +07:00 |
| Diff/syntax | node --check ของ tests ที่แก้/เพิ่ม และ git diff --check ผ่าน |

ตรวจ DB ใหม่: Flyway V1/V2/V3 และ V1000 test seed success ทั้งหมด; `information_schema.columns` ยืนยันคอลัมน์ patient_middle_name เดิมเป็น character varying(100) ไม่เพิ่มคอลัมน์ซ้ำ

หลังตรวจครบ เช็ก owner label แล้วลบเฉพาะ container/anonymous volume ของ P2 สำเร็จ; ports 18080/19001/5173 ไม่มี listener ของงานค้างอยู่ ไม่ล้างฐานจริงและไม่หยุด Docker Desktop

Browser พิสูจน์ register middleName/title/gender/address ว่าง, foreigner ไม่มี phone/birthday/passport/address, exact lookup ไม่ใช้ phone ที่ตรงกันเป็น national ID, profile compound names/middleName update/clear พร้อมอ่านกลับ, tests patient เดิมและ branch isolation P1 ผ่าน

Commission algorithms/permissions/ฐานคำนวณ/การปิดยอดไม่เปลี่ยน; commission regression 17 ข้อเป็นหลักฐานพฤติกรรมเดิมตาม assertions ไม่ใช่การรับรอง business rules ทุกกรณี

## Requirement matrix และงานที่ยังพัก

| Source | สถานะ / หลักฐาน |
|---|---|
| R5 / T20 | Backend exact lookup + browser adapter/API UI ผ่าน; hardware reader ยังไม่เชื่อม/ไม่ได้ทดสอบ |
| R6 / T16 | Canonical titles 5 กลุ่ม ไทย/อังกฤษ + profile round trips; UI bilingual labels |
| R7 / T16 | Male/Female/NotSpecified API round trips และ browser ไม่ overwrite title |
| R8 / T15 | DB column เดิม mapped, register/update/preserve/clear ผ่าน, compound names ไม่เสีย |
| R9–10 / T15 | Foreigner optional ทั้ง API/browser; address optional; phone ไทยคงเดิมและยังรอ Q1 |
| R11 | Q2 ยังเปิด: เก็บ logic name warning เดิม ไม่เพิ่ม hard-block หรือ auto-merge; repository เดิมค้น first/last แยก จึงยังไม่อ้างว่าป้องกันชื่อซ้ำครบ |
| T27 | allergy/chronic/consent/emergency รอ Q10 ก่อน field contract/migration/backfill |
| T30 | API/DB readback, adapter unit, browser, contract และ build ตรวจจริงตามขอบเขตที่แตะ |

ยังไม่เปลี่ยน HN/receipt format, migration/backfill หรือ commission; ไม่เริ่ม P3 ไม่ปิด Q1/Q2/Q10 และไม่อ้างผล GitHub Actions หรือ production RuntimeCheck ใหม่

Environment notes: ใช้ Node 24.18.0/Maven 3.9.16/Corretto21.0.12/Cypress16.1.0; มี warning เดิมของ Electron, Node DEP0190, Vite config import และ Cypress trash ลบ screenshots ของ RED ไม่สำเร็จ ผลผ่านใช้ runner/JUnit ใหม่ ไม่ใช้ภาพเก่าเป็นสถานะล่าสุด; SQLState23505 ใน reference-data suite เป็น duplicate-method negative tests ที่ assert409 ตามเดิม
