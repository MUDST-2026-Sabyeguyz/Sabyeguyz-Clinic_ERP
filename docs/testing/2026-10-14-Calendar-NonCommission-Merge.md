# Merge Google Calendar และ P1–P5

14 ตุลาคม 2026 (Asia/Bangkok), branch `feat/backend-v2-integration`

รวม local `b095b4b` (P0–P5) กับ remote `a4e56a1` (Google Calendar และเครื่องมือ CI/deploy). นายท่านอนุญาตให้แก้ conflict และ merge โดยยังไม่ได้สั่ง push.

## การแก้ conflict และจุดเชื่อมต่อ

- `AppointmentView.vue`: รวม lifecycle hooks ของ polling กับ debounce search/router ของ P3; คง flow ลงทะเบียนผู้ป่วยใหม่และสิทธิ์นัดหมาย
- ปฏิทินแสดงเวลาจริงของนัดนอกช่อง เช่น 09:45 พร้อมเบอร์โทร ไม่ใช้เวลาเริ่มช่องแทนเวลาจริง
- Polling ส่ง query เดิมทุกครั้ง เพื่อไม่ให้รายการที่ค้นหากลับเป็นข้อมูลทั้งหมดเมื่อครบ 15 วินาที; unit test ตรวจด้วย fake timers และตรวจว่าหยุด polling เมื่อออกจากหน้า
- คง Google Calendar link/status/sync UI จาก remote; API test เพิ่ม NEW contact → sync → move 09:45 → cancel โดยไม่สร้าง patient และเก็บ note/cancel reason ครบ
- แก้ unused query destructuring สองตัวในหน้าบริการให้ผ่าน ESLint โดยยังล้าง appointmentId/patientId ตามเดิม
- `SchemaCheck` ใช้ context แยกที่ไม่ scan configuration จึงต้องเพิ่ม CalendarProperties bean ให้ตรงกับ dependency ใหม่. Production RuntimeCheck ผ่านอยู่แล้ว; หลังแก้ test wiring SchemaCheck ผ่าน
- Browser กรณีจองซ้ำเดิมพึ่งสาขาแรกของชื่อที่เรียงลำดับ เมื่อ remote เปลี่ยนชื่อสาขาจึงสร้างอีกนัดกับนักกายภาพคนละสาขาและได้ 201 อย่างถูกต้อง. แก้ fixture ให้เลือกสาขา 01 และนักกายภาพคนเดียวกับนัดเดิมอย่างชัดเจน
- Test receipt snapshot สร้าง service ของตัวเองเพื่อเปลี่ยนชื่อ แทนการแก้ seeded service ที่ชุดอื่นใช้; scoped receipt API หลังแก้ผ่าน 9/9

## Verification บน merged working tree

- Backend `npm test`: Maven package และ Cypress **400/400**, exit 0
- Backend v2 PostgreSQL API: **217/217** จาก 24 specs รวม Calendar **13/13**, commission และ P1–P5, exit 0
- Frontend unit: **51/51** จาก 10 files; ESLint และ type-check/Vite build ผ่าน
- Production profile บน PostgreSQL ว่าง: `RUNTIME_CHECK_OK`; Flyway V1/V2/V3/V1001 ผ่าน
- Schema: **494 mapped columns, 0 problems**, Hibernate validate ผ่าน **45 entities, 22 controllers, 31 services**, `SCHEMA_CHECK_OK`
- Browser full suite รอบแรก 116/117 จาก fixture จองซ้ำที่เลือกคนละสาขา; หลังแก้ fixture รันใหม่ครบ **117/117** จาก 14 specs, exit 0

Google Calendar ใช้ fake provider ใน test profile ไม่มีการเชื่อมต่อบัญชีจริง. Q4/Q5/Q6/Q9 ของงาน non-commission ยังเปิดตามแผนเดิม; merge นี้ไม่ถือเป็นคำตอบของข้อกำหนดที่ค้าง. การเปลี่ยนไฟล์ CommissionClosingView ที่รับจาก remote เป็นการลบตัวแปร/ฟังก์ชันที่ไม่ได้ใช้ ไม่มีการแก้สูตร commission.
