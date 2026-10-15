# P8/P9 decisions and release gates — 15 October 2026

เจ้าของ: นายท่าน/Alice บน `feat/p8-p9-reports-release`; เพื่อนดูแล `feat/p6-p7-identifiers-courses`.

| Gate | คำตอบที่ต้องได้ | สถานะ |
|---|---|---|
| Q11 | ลูกค้าเก่า/ใหม่ตาม registration หรือ first visit; ช่วงยอดขายตาม saleDate และเงินรับตาม paymentDate; ต่ออายุ/อัปเดตแพ็กเกจเป็น metric หรือ action | ยังไม่มีคำตอบใน source/spec ที่ตรวจ |
| Q12 | owner/calendar ต่อสาขา, conflict/delete/privacy policy ของ Calendar, room states, delivery channel สำหรับ reset | ยังไม่มี business approval ครบ; existing Calendar implementation มี behavior ให้ review |
| Q3/Q4 | approved new HN/receipt format, uniqueness/reset/legacy mapping | เพื่อนระบุว่ายังรอคำตอบใน `edfc4e6` |
| Q7/Q13 | expiry boundary/extension role/shared members และ attachment policy | เพื่อนระบุว่ายังรอคำตอบใน `edfc4e6` |
| Q10 | active schema ของ allergy/consent/emergency และ retention ของ legacy fields | ยังไม่อนุมัติ; อยู่เฉพาะ legacy ไม่เท่ากับอ่านได้ใน v2 |
| P9 accounting mismatch | demo sales header รวม 45,800 แต่รายการขายรวม 47,300; existing importer ใช้รายการขาย เก็บ original ใน legacy และ report ระดับใบ | ยืนยัน mapping ปัจจุบันได้ แต่ยังไม่อนุมัติความต่างสำหรับ release |

ขอบเขตที่ทำได้: P8 audit + current-behavior regression; P9 migration rehearsal/manifest/FK and accounting checks/restore-repeatability และ CI schema enumeration. ไม่ปิด P8 dashboard semantics หรือ P9 final sign-off จากผลเครื่องมือเหล่านี้.

การเพิ่ม checker ไม่เปลี่ยน importer policy หรือ commission. รายงาน `receiptHeaderDifferences` ต้องตรวจทุกใบ; `releaseReady=false` แม้ `MIGRATION_REHEARSAL_OK` เพราะหมายถึง technical mapping/repeatability เท่านั้น.
