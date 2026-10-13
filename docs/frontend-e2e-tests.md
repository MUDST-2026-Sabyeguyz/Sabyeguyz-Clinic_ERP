# ชุดทดสอบ Cypress ฝั่ง Frontend (ทดสอบกับ backend v2 จริง)

ทดสอบหน้าเว็บของเจ้าหน้าที่ **ผ่านเบราว์เซอร์จริง** กับ backend v2 บน PostgreSQL จริง
(ไม่ใช่ mock) ใช้หลักเดียวกับชุดทดสอบ API ของ backend ([v2-api-tests.md](v2-api-tests.md))

## ทำงานอย่างไร

1. GitHub Actions (`.github/workflows/frontend-e2e-v2.yml`) เปิด PostgreSQL 16 → รัน backend v2
   (`mvn -Pcypress-v2 spring-boot:start`, พอร์ต 18080) → รัน Vite dev server (พอร์ต 5173, proxy `/api` ไป backend)
2. ก่อน **ทุกเทสต์** `support-v2/e2e.js` เรียก `POST /__cypress/reset` (มีเฉพาะตอนเทสต์) เพื่อล้างข้อมูลให้เหลือ
   ข้อมูลตั้งต้น + คอร์สทดสอบ `CYPRESS` (9,000 บาท, 5+1 ครั้ง) — เทสต์จึงไม่กระทบกัน
3. เทสต์เตรียมข้อมูลผ่าน API ตรง ๆ (`cy.api`, `cy.registerPatient`) แล้วเปิดหน้าด้วย `cy.visitAs('manager'|'therapist', path)`
   (ล็อกอินผ่าน API แล้วใส่ session ใน localStorage) จากนั้นกดปุ่มจริงบนหน้าจอ และตรวจผลทั้งบนหน้าจอและใน backend

## ไฟล์

| ไฟล์ | เนื้อหา |
|---|---|
| `frontend/cypress.v2.config.ts` | config (baseUrl, `expose.apiUrl`, พิมพ์บรรทัด `FAILED …` ใน log) |
| `frontend/cypress/support-v2/` | คำสั่งช่วย (`api`, `visitAs`, `pickDate`, `registerPatient`) และ reset ก่อนทุกเทสต์ |
| `frontend/cypress/e2e-v2/auth.cy.js` | ล็อกอิน/ออกจากระบบ, สิทธิ์ Manager vs นักกายภาพ, หน้าที่ปิดสำหรับนักกายภาพ |
| `patients.cy.js` | ลงทะเบียน (ไทย/ต่างชาติ), ค้นหา/กรอง/เรียง/แบ่งหน้า, โปรไฟล์และการแก้ไข |
| `appointments.cy.js` | รายการ, จองผ่านฟอร์ม, เลื่อน, เช็คอิน→เริ่ม→เสร็จ, ยืนยัน, ยกเลิกต้องมีเหตุผล, ไม่มาตามนัด |
| `sales.cy.js` | ขาย Single Visit / คอร์ส, รายการธุรกรรม, ยกเลิก (Void) พร้อมเหตุผลและประวัติ, ยกเลิกการใช้คอร์ส |
| `courses.cy.js` | จัดการคอร์สผู้ป่วย, ใช้สิทธิ์, ประวัติการใช้, โอนสิทธิ์ |
| `dashboard.cy.js`, `reports.cy.js` | ตัวเลขสรุป, รายงานยอดขาย/คงเหลือ/โอน/คอมมิชชัน |
| `commission.cy.js`, `commission-tiers.cy.js` | รายการคอมมิชชัน, ปิดรอบรายเดือน, ตั้งค่า Tier |
| `settings.cy.js` | สาขา, ผู้ใช้, ช่องทางชำระเงิน, ห้อง, บริการ |

## สิ่งที่เทสต์นี้ทำให้พบและแก้ (frontend ↔ v2)

- ชื่อสถานะนัดหมาย/ประเภทรายได้/ประเภท ledger ของ v2 เป็น PascalCase (`InService`, `NoShow`, `CoursePurchase`,
  `Treatment`) แต่หน้าจอใช้ `IN_SERVICE`, `NO_SHOW`, `COURSE`, `USAGE` → เพิ่มตัวแปลงใน `frontend/src/api/client.ts`
  (รองรับ backend เก่าด้วย) และรองรับสถานะ `Unconfirmed` + ปุ่มยืนยันนัด
- การขายใช้รหัสสาขา `RP` ตายตัว → ใช้สาขาของผู้ป่วย
- ออกจากระบบไม่ลบ `authUser` → กลับเข้าหน้าเจ้าหน้าที่ได้แล้วโดน 401 → ล้าง session ทั้งหมด และ 401 ไม่ทำให้เกิด error ค้าง
- ปิดรอบรายเดือน: backend ส่งเฉพาะรอบที่ปิดแล้ว ทำให้ปุ่ม "ปิดรอบทั้งหมด" กดไม่ได้เลย → backend ส่งแถว `OPEN`
  ของผู้ขายที่มียอดแต่ยังไม่ปิด; หน้าจอใช้เดือนปัจจุบันแทน `2026-08` ตายตัว
- อัตราคอมมิชชันแสดงเป็น `7.000000000000001%` → ปัดทศนิยม

## รันเอง

ต้องมี PostgreSQL + backend v2 รันอยู่ (ดู `v2-api-tests.md`) แล้ว

```bash
cd frontend
npm run dev -- --host localhost --port 5173 &
CYPRESS_API_URL=http://127.0.0.1:18080 VITE_API_PROXY_TARGET=http://127.0.0.1:18080 \
  npx cypress run --config-file cypress.v2.config.ts
```

(CI เป็นที่เดียวที่รันครบทุกอย่างอัตโนมัติ — ดูผลที่แท็บ Actions → "Frontend E2E (v2 backend)")
