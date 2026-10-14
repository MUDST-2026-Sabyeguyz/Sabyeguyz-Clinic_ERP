# P5: ประวัติใบเสร็จผู้ป่วยและการพิมพ์

14 ตุลาคม 2026 (Asia/Bangkok) · branch `codex/noncommission-p4-p5` · ต่อจาก P4 commit `b1e5f26`

## Requirement และขอบเขต

- **Confirmed Requirement:** R13 ประวัติใบเสร็จผู้ป่วย, R25 ซ่อน Assessment จากการขายใหม่แต่คงประวัติ, R28 จ่ายสำเร็จแล้วเปิดเอกสารสำหรับพิมพ์, R32 ภาษี 0 ตามเอกสารที่ได้รับ
- **Q4 ค้าง:** ยังใช้เลขใบเสร็จและหน่วยรายการที่บันทึกอยู่; ไม่กำหนดสูตรเลขใหม่หรือเปลี่ยนราคา/จำนวนครั้งของคอร์ส
- **Q5/Q6 นายท่านให้พัก:** ไม่เปลี่ยนเพดานส่วนลด การปัดทศนิยม การผ่อนหลายงวด หรือสูตรสร้าง payment label; อ่านค่าที่บันทึกอยู่
- **Q9 ค้าง:** คงสิทธิ์อ่าน patient/sales ปัจจุบัน; ไม่อ้างว่านโยบายสิทธิ์นัดหมายครอบคลุมใบเสร็จ

## ผลลัพธ์ที่ตรวจ

- `GET /api/payments?receiptsOnly=true` เลือกหนึ่ง legacy transaction ID ต่อใบเสร็จ **ก่อน** pagination, กรอง patient/branch ฝั่ง server, เรียงใหม่ก่อนเก่า; default list ยังเป็นรายการขายรายบรรทัดเพื่อรักษา contract เดิม
- Summary/detail เพิ่ม `receiptId` แยกจาก `id` เดิม; หน้า `/staff/receipts/:id` ใช้ legacy transaction ID เพื่ออ่าน `GET /api/receipts/{id}`. ยอดสุทธิ ส่วนลดท้ายบิล ฐานภาษี ภาษี และยอดรับชำระแสดงจาก receipt/payment ที่บันทึกจริง ไม่คำนวณยอดสุทธิจากเงินที่จ่ายบางส่วน
- เมื่อ POST ชำระสำเร็จ เปิดหน้าใบเสร็จทันที; reload, เปิดจาก history และพิมพ์ใช้ GET เท่านั้น. POST ล้มเหลวอยู่ที่ฟอร์ม; ถ้านำทางหลัง POST สำเร็จล้มเหลว จะมีลิงก์ไปใบเสร็จและ guard ไม่ให้กดสร้างซ้ำจากหน้าเดิม
- History แสดง 20 ใบต่อหน้าและมีหน้าถัดไป; ตัวค้นหา/ตัวกรองเพิ่มเติมระบุชัดว่าใช้เฉพาะหน้าปัจจุบัน. หน้าพิมพ์ใช้สาขาของใบเสร็จและชื่อรายการ snapshot ณ วันขาย
- Migration `V1001` เพิ่ม enum `Assessment` สำหรับข้อมูลย้อนหลังเท่านั้น; service validation ยังคงปฏิเสธการขาย Assessment ใหม่. Legacy migration รักษาประเภทนี้ไว้; CI fixture และ verifier เพิ่มกรณีอ่านกลับ

## หลักฐานทดสอบ

- ก่อนแก้: API receipt-history RED จากการแบ่งหน้าซ้ำและยอดบิล; historical Assessment fixture 409 เพราะ PostgreSQL enum ไม่มีค่า; snapshot name RED ชัดเจน: ชื่อที่เปลี่ยนหลังขายปรากฏบนใบเสร็จเดิม
- หลังแก้: API receipt-history **9/9**, frontend unit **49/49** จาก 9 files, `npm run build` (type-check และ Vite build) ผ่าน
- Browser v2 full suite กับ PostgreSQL จริง **117/117** จาก 14 specs; P5 history/print **5/5**, รวม partial payment, reload ไม่เพิ่มใบเสร็จ, ประวัติข้ามหน้า, legacy/native ID ต่างกัน และ Assessment ย้อนหลัง
- Backend v2 full suite หลังแก้ snapshot ผ่าน **204/204** จาก 23 specs รวม commission regression; receipt-history scoped **9/9**. Backend `npm test` ที่ CI ใช้ผ่าน **400/400** จาก 17 specs และ Maven package ผ่าน
- Dry-run legacy migration บน DB ทดสอบแยก: legacy `ASSESSMENT` **17 รายการ**, v2 `Assessment` **17 รายการ**; fixture ใหม่เลขเก่า `RC2601010003` map เป็น `RE26020002`. เปิด production-profile v2 บน DB ที่ย้ายแล้วและอ่าน authenticated list/detail ผ่าน ได้ชนิด `Assessment`, ยอดสุทธิ 600 (`MIGRATED_ASSESSMENT_API_OK`). Shell verifier ผ่าน `bash -n` ใน container; การรัน GitHub Actions จริงยังเป็น gate ของ CI
- Production-profile `RuntimeCheck` บนฐานว่างผ่าน `RUNTIME_CHECK_OK` และยืนยัน Flyway V1/V2/V3/V1001; `SchemaCheck` ผ่าน 491 mapped columns, 0 problems, 45 entities, 21 controllers, 30 services และ `SCHEMA_CHECK_OK`

`receiptNo` ของ legacy ถูกแปลงระหว่าง migration ตาม mapping เดิม; P5 ไม่เปลี่ยนสูตรเลขใบเสร็จ. ชื่อผู้ป่วยและรายละเอียดที่อยู่สาขาบนหน้าพิมพ์ยังอ่านจากระเบียนปัจจุบัน เพราะ schema ไม่เก็บ snapshot ส่วนนั้น; ชื่อรายการขายใช้ snapshot ที่มีอยู่แล้ว. ไม่มีการแก้สูตร/ledger commission.
