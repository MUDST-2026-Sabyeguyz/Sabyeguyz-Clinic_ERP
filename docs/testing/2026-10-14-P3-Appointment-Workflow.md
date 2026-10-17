# P3: Appointment workflow ส่วนที่ไม่ติด Q8

วันที่: 14 ตุลาคม 2026 (Asia/Bangkok)

Branch: `codex/non-commission-backend`, parent `6f9920c` (P2)

สถานะ: acceptance ของส่วนไม่ติด Q8 ผ่าน พร้อม focused commit; รายการที่ติดคำถามยังไม่ปิด

## Scope และ requirements

Confirmed Requirement: ทำตามแผนทีละ P ตรวจ req ก่อน commit และไม่เปลี่ยนระบบ commission; Q9 พักตามคำสั่งเดิม

ตรวจข้อความจาก `C:/Users/napal/Downloads/Requirement + Feedback Update.pdf` หน้า 2 สด เทียบ R17–22/24–25 และ scope/plan ที่อนุมัติ; R15–16/T17–19 อิง requirement mapping ใน scope เดิม

| Requirement | ผลในรอบนี้ |
|---|---|
| R12/17–19 | NEW รับชื่อจริงหรือชื่อเล่น+โทร โดยยังไม่สร้าง Patient; branch/therapist/time บังคับ ไม่มี room; CURRENT ใช้ข้อมูลทะเบียนจริง; UI แสดงประเภทจาก API และเบอร์บนรายการ/ปฏิทิน |
| R15–16 | มีเครื่องหมายช่องบังคับ; NEW เลือก SingleVisit/Course; branch ของ non-Manager มาจาก session และ server ตรวจ P1 ซ้ำ |
| R20 | query repository ค้นชื่อจริง/กลาง/นามสกุล/ชื่อเต็ม/ชื่อเล่น/HN/โทร และ NEW contact ก่อน pagination; ไม่แสดงตัวกรองบริการทั้งหมด |
| R21–22 | รอยืนยันยังแก้ไขเวลา/หมายเหตุได้; Confirmed เปิดลงทะเบียน/บริการแทนปุ่มแก้ไข ทั้งรายการและปฏิทิน; ไม่อนุมาน receipt = Completed |
| R24 | CURRENT เปิดบริการพร้อม patientId/appointmentId; NEW confirm→register→link→services; หน้าบริการตรวจ patient ตรงนัดและล้างบริบทเมื่อเปลี่ยนผู้ป่วย |
| R25 | NEW requested type เหลือ SingleVisit/Course; การซ่อน Assessment จาก sale/catalog และรักษาประวัติอยู่ P5 ไม่ปิดด้วยรอบนี้ |
| T17–19 | note persist และ cancelReason readback; เก็บสถานะ/transition เดิม; duration และนิยามใช้บริการแล้วรอ Q8 |

Assumption: non-Manager ใช้ home branch ของ session ใน UI; server เป็นผู้ตรวจสิทธิ์จาก DB ตาม P1 ไม่เชื่อ branch ใน browser เพียงอย่างเดียว

Open Question: Q8 ยังไม่ตอบเรื่อง duration/endAt, การย้าย therapist และ Completed กับ receipt; endAt ยังเท่ากับ startAt ไม่ถือว่าช่วงเวลานัดครบ; Q1/Q2/Q10 และ Q9 จากขั้นก่อนยังเปิด Google Calendar สองทางยังไม่ implement

## Implementation

- Map `Appointment.notes` ไป `appointment.notes TEXT` เดิม ไม่เพิ่ม migration หรือแก้ V1–V3; PATCH ไม่ส่ง note คงเดิม ส่งข้อความแก้ ส่ง null ล้าง
- cancelReason อ่านประวัติ Cancelled ล่าสุด เรียง changedAt/id; API เพิ่ม patient.phone และส่ง nullable id/hn สำหรับ NEW
- Search LEFT JOIN patient จึงไม่ทิ้ง NEW; server จำกัด branch ก่อน query/pagination; page >= 0 และ size 1–100
- link-patient ล็อกแถวก่อนโหลด entity ครั้งแรก ป้องกัน persistence context เก็บ NEW เก่าระหว่างรอล็อก; concurrent links สำเร็จหนึ่ง อีกคำขอ 400 และไม่ relink เงียบ
- Reschedule ไม่ตรวจ slot ตัวเองเป็น conflict; ยังคงกฎชนเวลาของคนอื่นเดิม ไม่เปลี่ยน duration/therapist
- UI ลงทะเบียนผูกนัดต้องเป็น NEW+CONFIRMED; จำ patientId หลังสร้างเพื่อ retry link โดยไม่สร้างซ้ำในหน้าปัจจุบัน; ถ้า link commit แล้ว response สูญหายตรวจ exact patientId ได้
- เมื่อเปลี่ยนผู้ป่วยในหน้าบริการ ล้าง patientId/appointmentId query; ไม่แตะการสร้างบิล การชำระ หรือ commission

ข้อจำกัด retry: การลงทะเบียนและ link ยังเป็นสอง requests; หาก register สำเร็จแต่ response register สูญหาย หรือ reload หน้าหลัง register ยังไม่ได้รับรอง idempotency ข้าม session; ไม่ auto-merge ผู้ป่วยตามชื่อหรือเปลี่ยน Q2

## Verification evidence

ใช้ PostgreSQL16 container แยก `alice-noncommission-p3-20261014`, label `codex.task=noncommission-p3-20261014`, bind `127.0.0.1:55434`, database `noncommission_p3`; password สุ่มไม่อยู่ใน Git; API/browser fixture reset ทำตามลำดับ

| Check | ผลจริง |
|---|---|
| RED API ใหม่ | 7 cases, 1 pass/6 fail ก่อน implementation รวม concurrent link race |
| RED browser ใหม่เดิม | 3 cases, fail ทั้งหมดก่อน frontend implementation |
| Scoped API GREEN | appointment-workflow 7/7 รวม notes preserve/edit/clear และ concurrent links |
| Full API v2 | **183/183 จาก 21 specs**, fail/pending/skipped 0, exit 0; รวม commission 12+5 และ P1 branch 18; runner หยุด 15:47:00 +07:00 |
| Frontend unit | **30/30 จาก 5 files**, exit 0; rerun นอก sandbox หลัง temp rename EPERM; จบ 15:48:54 +07:00 |
| Browser broader regression | **57/57**, exit 0: workflow5, appointments13, branch3, patients23, sales13; หยุด server 15:52:57 +07:00 |
| Browser final appointment acceptance | **21/21**, exit 0: workflow5, appointments13, branch3 หลังแก้ R22/ถอด room/calendar dispatch; server หยุด 15:54:48 +07:00 |
| Frontend type-check/production build | vue-tsc + Vite ผ่าน exit 0 หลัง code ล่าสุด |
| Backend package | BUILD SUCCESS exit 0 เวลา 15:51:24 +07:00; ไม่ถือ Maven package เป็นหลักฐาน API runtime แทน 183 cases |
| SchemaCheck | **491 mapped columns, 0 problems**, validate45 entities, wire21 controllers/30 services, SCHEMA_CHECK_OK exit0 |

Review พบและแก้ NEW edit guard, openEdit ตั้งประเภททับ, reset contactPhone/requestedServiceType, service context เมื่อเปลี่ยนผู้ป่วย และ calendar เข้าฟอร์มแก้ไขข้ามสถานะ; มี browser assertions ครอบคลุม

Status mapping: Unconfirmed/Rescheduled→รอยืนยัน; Confirmed/Arrived/InService→กลุ่มยืนยัน; Completed→ใช้บริการแล้ว; Cancelled/NoShow→กลุ่มยกเลิก โดย action select ยังคงสถานะจริงและ enum ประวัติไม่ถูกลบ

Commission source/formula/permission/closing ไม่มี diff; commission17 assertions และ browser sales13 ผ่านเป็น regression evidence ตามกรณีทดสอบ ไม่ใช่การรับรอง business rules ทุกกรณี; ไม่อ้างผล production หรือ GitHub Actions

Environment notes: Cypress Electron/Node DEP0190/Vite config warning เดิม; Cypress trash ลบ screenshot เก่าจาก RED ไม่สำเร็จไม่เปลี่ยน exit code; ใช้ผล runner ใหม่ ไม่ใช้ภาพเก่าเป็นสถานะล่าสุด

ตรวจ node --check ของ API/browser specs และ git diff --check ผ่าน; cleanup ตรวจ owner label ก่อนลบเฉพาะ container/volume ของ P3 และตรวจพอร์ต test ไม่มี listener ค้าง ไม่หยุด Docker Desktop
