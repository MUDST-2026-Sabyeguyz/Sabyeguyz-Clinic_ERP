# P8/P9 decisions and release gates — 15 October 2026

เจ้าของ: นายท่าน/Alice บน `feat/p8-p9-reports-release`; เพื่อนดูแล `feat/p6-p7-identifiers-courses`.

| Gate | คำตอบที่ต้องได้ | สถานะ |
|---|---|---|
| Q11 | ลูกค้าเก่า/ใหม่ตาม registration; ต่ออายุ/อัปเดตแพ็กเกจเป็น metric | นายท่านตอบ 15 ต.ค.: ใหม่=ลงทะเบียนในช่วงแม้ยังไม่มา; เก่า=ลงทะเบียนก่อนช่วงและมาใช้บริการในช่วง; แสดงจำนวนต่ออายุ/แก้แม่แบบคอร์ส Back Office |
| Q12 | owner/calendar ต่อสาขา, conflict/delete/privacy policy ของ Calendar, room states, delivery channel สำหรับ reset | ยังไม่มี business approval ครบ; existing Calendar implementation มี behavior ให้ review |
| Q3/Q4 | approved new HN/receipt format, uniqueness/reset/legacy mapping | เพื่อนระบุว่ายังรอคำตอบใน `edfc4e6` |
| Q7/Q13 | expiry boundary/extension role/shared members และ attachment policy | เพื่อนระบุว่ายังรอคำตอบใน `edfc4e6` |
| Q10 | active schema ของ allergy/consent/emergency และ retention ของ legacy fields | ยังไม่อนุมัติ; อยู่เฉพาะ legacy ไม่เท่ากับอ่านได้ใน v2 |
| P9 accounting mismatch | demo sales header รวม 45,800 แต่รายการขายรวม 47,300; existing importer ใช้รายการขาย เก็บ original ใน legacy และ report ระดับใบ | ยืนยัน mapping ปัจจุบันได้ แต่ยังไม่อนุมัติความต่างสำหรับ release |

รอบก่อนตอบ Q11 ทำ P8 audit + current-behavior regression และ P9 migration rehearsal/manifest/FK/accounting/restore-repeatability/CI. หลังคำตอบ Q11 ทำ Dashboard metrics ตามหัวข้อด้านล่างแล้ว; ไม่ปิด P8 backlog Q12 หรือ P9 final sign-off จากคำตอบ/ผลชุดนี้.

การเพิ่ม checker ไม่เปลี่ยน importer policy หรือ commission. รายงาน `receiptHeaderDifferences` ต้องตรวจทุกใบ; `releaseReady=false` แม้ `MIGRATION_REHEARSAL_OK` เพราะหมายถึง technical mapping/repeatability เท่านั้น.

## Q11 approved follow-up (15 October)

- ลูกค้าใหม่: distinct patient ที่ `registered_at` อยู่ในช่วงวันที่เลือกแบบ Bangkok [from 00:00, วันถัดจาก to 00:00), ไม่ต้องมี visit; สาขาอิง registration branch.
- ลูกค้าเก่า: distinct patient ที่ลงทะเบียนก่อน from และมี visit Arrived/InService/Completed ในช่วง; สาขาอิง visit branch. นายท่านยืนยันนิยามนี้โดยตรง.
- ต่ออายุ: จำนวนแถวประวัติ `course_extension` ตาม `extended_at` และสาขาคอร์ส; ไม่สร้าง extension API/rule แทน P7.
- อัปเดตแพ็กเกจ: จำนวนครั้งที่แก้แม่แบบคอร์ส Back Office สำเร็จและมีการเปลี่ยนค่าจริง; ไม่นับ create/delete/no-op save. แม่แบบเป็น catalog กลาง จึงแสดง scope ทั้งคลินิกแม้กรองสาขา; เพิ่ม audit old/new/actor/time สำหรับการแก้ใหม่ ไม่อ้างว่ามีประวัติก่อนเริ่มบันทึก.
- ยอดขายสุทธิ: ผลรวม net ของ receipt ที่ไม่ Voided ตาม saleDate. เงินรับ: payments ที่สำเร็จตาม paymentDate (ก่อนหักคืนเงิน), ไม่เอายอดรับมาเรียกยอดขาย. กราฟ dailySales มีวันยอดศูนย์.
