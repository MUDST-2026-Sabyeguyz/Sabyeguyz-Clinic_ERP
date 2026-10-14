# P4: Payment method policy และ refund guard

วันที่14ตุลาคม2026 (Asia/Bangkok), base41a677b; branch codex/noncommission-p4-p5

## Scope และ gate

Confirmed Requirement: R30–31 หนึ่งวิธีต่อบิล/ชื่อพร้อมเพย์/payment labels และ R33 ห้าม refund หลังใช้คอร์ส ตามแผน P4 โดยไม่เปลี่ยน commission

User decision: พัก Q5/Q6 ไว้ก่อนตามคำตอบใน chat ไม่เปลี่ยน discount precision/ceiling หรือการ derive Deposit/Installment/ยอด0/จำนวนงวด; รักษา behavior เดิมของหลาย payment records ที่ canonical method เดียวกัน ไม่ถือว่าคำถามเรื่องงวดได้รับอนุมัติแล้ว

## การเปลี่ยนแปลง

- Validate canonical methods ใน create ก่อนสร้างreceipt/line/payment และคง payments[] contract; QR alias เป็น PromptPay
- PaymentTransactionService ล็อก Receipt ก่อนตรวจวิธีของ active payments; different method ภายหลังปฏิเสธ400 ส่วน voidPayment ใช้ lockเดียวกันเมื่อเปลี่ยน active payment state
- Refund request และ completion ล็อก course ของ receipt ตาม ID order เช่นเดียวกับ course-usage API แล้วเช็กว่ามี usage history หรือไม่ ก่อนเขียน refund/cancellation; พบได้409 และคง refund Pending เมื่อตรวจตอน complete ไม่ผ่าน
- Any usage history รวม reversed usage ถือว่าเคยใช้แล้วเพื่อ R33; ไม่ลบ/แก้ historical refunds และไม่เปลี่ยน void บิลที่ยังไม่ใช้
- API summary/detail เพิ่ม persisted paymentLabel และ UI map FullyPaid/Installment/Deposit→ชำระเต็ม/ผ่อน/มัดจำ แยกจาก lifecycle PAID/VOIDED; ไม่คำนวณใหม่จาก boolean
- แสดง QR/PromptPay เป็นพร้อมเพย์โดยรักษา request code และลบแต้ม/วงเงินคงเหลือสมมติ
- ไม่มี migration และไม่แก้ commission algorithm, monthly close, treatment fee, allocation/reversal หรือฐานส่วนลด

## Verification

- Backend initial RED9behavior failures +1infra error (parallel task allowlist ยังไม่รับ test-only endpoint); ไม่อ้าง infra error ว่าเป็น concurrency behavior RED
- Frontend RED11กรณี component display → GREEN unit42/42, type-check/buildผ่านตามผลimplementer
- Read-only spec/code review ไม่พบ actionable application defect; tests/runtimeยังต้องใช้ผลจริงแยกจาก static review

Automatic approval review ปฏิเสธคำสั่งทดลองถอด guard ชั่วคราวเพราะไม่มี guarantee การคืนโค้ด คำสั่งนั้นไม่ได้รัน; ยกเลิกวิธีดังกล่าวและใช้ test-only deterministic lock contention แทน ไม่ bypass การตรวจ

P5 receipt aggregation/net/print เป็นขั้นถัดไป ไม่อ้างว่าปิดด้วย P4; Q4/Q9ยังพักตามแผน

Full test:v2 รอบแรก194/195; failureเดียวคือ regressionเดิมที่อนุญาตCash+QR splitpayment ซึ่งขัดR31ใหม่ จึงปรับfixtureเป็นCashสองrecordsและเพิ่มassert201 ไม่ลดcoverageจำนวนrecords และไม่ตีความQ6ว่าอนุมัติงวดแล้ว

ผล acceptance โค้ดรวม:

| Check | ผล |
|---|---|
| Focused real PostgreSQL |12/12 รวมdifferent-method200/400และrefund lock waitจริงทั้งrequest/complete|
| Backend CI command npm test |400/400 จาก17specs, clean package BUILD SUCCESS, exit0|
| Full test:v2 รอบสุดท้าย |195/195 จาก22specs, fail/pending/skipped0, exit0 รวมcommission17/17|
| Frontend unit/build |42/42 จาก7files, vue-tsc+Vite buildผ่าน exit0|
| RuntimeCheck |RUNTIME_CHECK_OK บน DBใหม่ noncommission_p4_runtime, exit0|
| SchemaCheck |491mapped columns/0problems,45entities/21controllers/30services,SCHEMA_CHECK_OK exit0|
| Browser |16/16: payment-policy3และsales13, exit0; testserversหยุดแล้ว|

Dockerทดสอบ codex-noncommission-p4-pg, labelcodex.task=noncommission-p4-20261014, PostgreSQL16, localhost55436, DB/userclinic_p4; passwordสุ่มไม่อยู่ในGit คงcontainerไว้ต่อP5 ไม่มีproductionDBถูกแก้

API/browser testsทำตามลำดับเพื่อไม่ให้fixture resetชนกัน; RuntimeCheckใช้DBคนละชื่อจึงไม่ปนfixtures LogsของP4อยู่ignored .superpowers/sdd/.../evidence-p4 ไม่ใช้ภาพเก่าหรือagentreviewแทนผลruntime
