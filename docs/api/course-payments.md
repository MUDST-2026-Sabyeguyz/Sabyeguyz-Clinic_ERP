# Course payments API contract

สถานะ: backend implementation สำหรับส่งต่อ frontend, 2 พฤศจิกายน 2026 (Asia/Bangkok)

ขอบเขตคือรับเงินคอร์สเดิมหลายใบ อ่าน/พิมพ์ประวัติ และ shared refund ตาม DD#11 กับ README handoff ทั้งสามไฟล์ ไม่มีการเปลี่ยน commission/Tier/schema การซื้อครั้งแรกและ legacy routes คงเดิม

## Decisions ที่ผู้ใช้ยืนยันในแชทนี้

| เรื่อง | ข้อสรุป | ผู้ยืนยัน |
|---|---|---|
| Refund roles | Manager-only ทุก action ก่อน; mutation ต้อง SALE_CANCEL | ผู้ใช้ |
| Approval | คงอนุมัติ/ตัดสิทธิ์/Completed ใน complete() เดิม ไม่มี approve route แยก | ข้อเสนอที่ผู้ใช้ตอบรับร่วมกับการคง refund baseline |
| Read scope | คง BranchAccessPolicy เดิม และ course history fail ทั้งชุดถ้ามีใบที่อ่านไม่ได้ | ผู้ใช้ |
| Idempotency | ใช้ audit_log เดิมร่วม database transaction lock ได้ ไม่เพิ่ม schema | ผู้ใช้ |
| Routes/DTO | ใช้ namespace แยกตามแผนที่ผู้ใช้สั่งให้ดำเนินการ; frontend ยังต้องเชื่อม UI ตาม contract นี้ | การดำเนินงาน backend ตามแผน ไม่ใช่หลักฐาน frontend integration |

DD ระบุ Therapist บันทึกยอดคืนได้ แต่ release นี้คง Manager-only ตามคำตอบผู้ใช้ ไม่ปลด guard ทั้ง class เพื่อเดา policy ใหม่

## Identity และ routes

| Method / path | ID / ผล |
|---|---|
| POST /api/course-payment-collections | รับเงินคอร์สเดิม; 201 {receiptId,paymentId,receiptNo,netAmount} |
| GET /api/receipt-documents | query patientId,branchCode,page=0,size=20; 200 {items,page,size,totalItems,totalPages} |
| GET /api/receipt-documents/{receiptId} | Receipt ID จริง; 200 Document |
| GET /api/courses/{courseId}/receipt-history | 200 Document[] รวมใบซื้อแรกและใบรับเงินถัดไป |
| POST /api/receipt-documents/{receiptId}/void | collection-only receipt; 200 {receiptId,paymentId,cancellationId} |
| GET /api/payments และ GET /api/receipts/{id} | legacy: items[].id และ path id ยังคือ SalesTransaction ID |
| POST/GET /api/payments/{paymentId}/refunds | refund payment ต้นทาง ไม่ใช่ sale/receipt ID |
| POST /api/refunds/{refundId}/complete, /void, /fail, /retry | refund ID; คง state machine เดิม |

API ใหม่รับ role Manager/Therapist ที่ authenticated; collection void ตรวจ SALE_CANCEL ใน service; refund ทุก route Manager-only และ mutation ตรวจ SALE_CANCEL

ผู้ใช้ทั่วไปเข้าถึงเฉพาะ home branch ส่วน Manager เข้าถึงทุกสาขาได้ตาม baseline: detail/history/mutation ที่ระบุสาขาปิดถูกปฏิเสธ; Manager search ที่ไม่ส่ง branchCode ยังค้นรวมสาขาปิดได้ตาม search เดิม ไม่ถือว่าเป็นสิทธิ์แก้รายการของสาขาปิด

## Collection request

Header Idempotency-Key จำเป็น: 8–128 ตัวอักษร A-Z a-z 0-9 . _ : - เช่น UUID ที่ frontend สร้างหนึ่งครั้งต่อความตั้งใจรับเงินหนึ่งรายการ

```json
{
  "patientId": 123,
  "branchCode": "01",
  "items": [{"courseId": 456, "amount": 10000.00}],
  "billDiscountType": "FIXED",
  "billDiscountValue": 3000.00,
  "paymentLabelCode": "INSTALLMENT",
  "paymentMethodCode": "BankTransfer",
  "paymentAmount": 7000.00
}
```

- ผู้ใช้กรอก amount และส่วนลดเอง ไม่ดึง 3000 จากประวัติมาหักอัตโนมัติ
- patient/branch/course ต้องมีอยู่จริง คอร์สทุกตัวมีผู้ซื้อเดียวกับ patientId และไม่ซ้ำในใบ
- amount/paymentAmount ไม่ติดลบ และทศนิยมไม่เกิน 2 ตำแหน่ง (decimal12,2); billDiscountValue ไม่ติดลบ decimal12,4 ส่วน Fixed ยังต้องไม่เกิน2ตำแหน่งตาม Discount เดิม
- ส่วนลดใช้ Discount.of: FIXED/AMOUNT/BAHT และ PERCENT/PERCENTAGE; ไม่ส่ง type/value เมื่อไม่มีส่วนลด ไม่มีส่วนลดรายรายการใน collection DTO
- paymentAmount เท่ากับยอดสุทธิของใบ รองรับ0สุทธิ หนึ่ง Completed payment/ใบ และหนึ่งช่องทาง CreditCard/PromptPay/BankTransfer ที่ Enabled
- DEPOSIT/INSTALLMENT/FULL เป็นป้าย manual ที่ Enabled ไม่คำนวณยอดค้าง ไม่บล็อกใช้คอร์สเพราะป้ายหรือจ่ายไม่ครบ
- actor มาจาก JWT; actorId ที่ client ส่งไม่มีผล ไม่สร้าง Course/SalesTransaction/Visit/สิทธิ์ใหม่

## Idempotency และ failure semantics

Scope = authenticated actor ID + Idempotency-Key; คนละ actor ใช้ key เดียวกันถือเป็นคนละ scope ห้ามแชร์ key ระหว่างบัญชีเพื่อหวัง replay ข้ามคน

Fingerprint normalize ลำดับ items ตาม courseId, notation ของ BigDecimal, discount aliases, label, branch/patient/payment method ID และ amount; same scope/key + normalized payload เดิมคืน201พร้อม body/IDsเดิม keyเดิม payloadต่างคืน409 คำขอใหม่ที่รับยอดเท่ากันใช้ key ใหม่ได้

ใช้ SHA-256 เต็มใน record_id รูป Base64URL43ตัวอักษรของ audit_log table_name=course_payment_collection_request; new_value มี version/fingerprint/result ไม่เก็บ key ดิบ Transaction-level PostgreSQL advisory lock แยกตาม digest ทำงานข้าม application instances ที่ใช้ฐานเดียวกัน จากนั้นอ่าน audit ใหม่ภายใต้ READ_COMMITTED

Receipt/items/payment/domain audit/replay audit อยู่ใน transaction เดียวกัน ถ้าล้มเหลวก่อน commit ไม่มีเงินหรือ key ค้าง Retry keyเดิมทำใหม่ได้ ถ้า commitแล้วแต่ responseสูญหายให้ retry keyเดิมและ payloadเดิม ระบบอ่านผลจาก DB แม้ application restart

การ replay ตรวจ current authentication/role/branch อีกครั้ง จึงอาจ403ถ้าสิทธิ์หรือสาขาเปลี่ยน ไม่ใช่ช่องทางข้าม authorization ข้อมูล replay ต้องเก็บถาวรพร้อม audit/receipt ไม่ purge เฉพาะ audit namespace นี้โดยไม่มีแผนรักษา idempotency ไม่มี TTL ที่ลบเอง ไม่มี in-memory cache/lock เป็นตัวรับประกัน และไม่มี migration ใหม่

Test จำลอง failure ตอนเขียน replay audit ก่อน commit และทดสอบ restartหลังcommit ไม่ใช่การอ้างว่าได้ kill OS process ณ ทุกจังหวะ PostgreSQL transaction เป็น boundary ที่รับประกันความเป็นอะตอม

## Receipt detail / history / print

Document มี receiptId,receiptNo,patientId,branchId,saleDate,receiptStatus,paymentLabelCode,paymentLabelName,subtotalBeforeDiscount,billDiscountAmount,netAmount และ:

- sales[]: transactionId,lineNo,transactionType,itemNameSnapshot,quantity,unitPrice,amount,lineDiscountAmount,allocatedBillDiscountAmount,effective
- coursePayments[]: courseId,itemNameSnapshot,amount,allocatedBillDiscountAmount,netAmount
- payments[]: paymentId,paymentDate,paymentMethod,amount,status (Completed/Voided)
- patient: {id,hn,fullName}
- branch: {id,code,name,address,phone,email,licenseNo}

Search items มี header IDs/date/status/label/netAmount + patient/branch/payments เพื่อแสดงวันที่รับเงินจริงและช่องทางได้ ครบโดยไม่ joinคูณแถว page>=0,size1..100

saleDate เป็น LocalDateTime ไม่เติม Z เอง paymentDate เป็น Instant; แสดงวันที่ตาม Asia/Bangkok ใน UI ใบเสร็จใช้รูปแบบปกติ หัวเอกสารใช้สาขาที่ออก ไม่มี received_amount/receipt_type หรือภาษีใหม่ คง legacy tax fields เฉพาะ routeเดิม ไม่บอกว่ารับเงินเพิ่มคือการซื้อใหม่ ข้อมูล buyer/branch display มาจาก entityปัจจุบัน ส่วนชื่อรายการ/ยอดเก็บ snapshotตาม serviceเดิม

## Collection-only void

```json
{"reasonCode":"PatientRequested","reason":"ผู้ป่วยขอยกเลิกรายการรับเงิน"}
```

ต้อง Issued มี Receipt_course_payment ไม่มี Sales_transaction ไม่ใช่ใบซื้อแรก/ใบผสม reasonCodeจำเป็น (WrongEntryOrService,DuplicateEntry,PatientRequested,PaymentFailedOrIncorrectAmount,Other) และ reasonไม่ว่าง <=255ตัวอักษร Serviceตรวจbranch/SALE_CANCEL/active-refundและใช้ receipt/payment lock เดียวกับrefund Voidเฉพาะใบและเงินครั้งนั้น ไม่กลับสิทธิ์อัตโนมัติ

## Refund

Request เดิมเพิ่ม optional reasonCode ถ้าไม่ส่งใช้ Other เพื่อรักษา clientเก่า; invalid enumคืน400 reasonเก็บ trimแล้วไม่ว่าง/<=255

```json
{
  "amount": 500.00,
  "paymentMethodCode": "BankTransfer",
  "reasonCode": "PatientRequested",
  "reason": "คืนเงินส่วนที่ยังไม่ใช้",
  "items": [{"courseId":456,"refundAmount":500.00,"cancelledVisits":null,"rightsCancellationItemId":789}]
}
```

รายการหลัก: rightsCancellationItemId=null,cancelledVisits>=1 รายการร่วมอ้าง courseRefundItemIdของหลักโดยตรง,cancelledVisits=null Response itemsมี courseRefundItemId/rightsCancellationItemId ใช้เลือกกลุ่มได้

คอร์สต้องสัมพันธ์กับ original payment ผ่านใบซื้อแรกหรือ Receipt_course_payment ยอดรวมไม่เกินpayment/ยอดสุทธิคอร์ส ตัดสิทธิ์ครั้งเดียว รายการร่วมcompleteก่อนหลักได้ retryไม่ตัดซ้ำ Completedห้ามVoid Failedไม่ใช่Voided กลับเฉพาะCancellationจริงเมื่อทั้งกลุ่มVoidedและไม่มีCompleted คืนผู้ถือสิทธิ์เดิมทุกคนแยกกลุ่มคอร์ส

complete()รวมapprovalและCompletedในtransactionเดียว ไม่มีapprove routeใหม่ กรณีcutก่อนcompleteที่testsเรียกrights serviceโดยตรงเป็นservice invariantไม่ใช่หลักฐานUI flow

## Errors / verification / ownership

400 validation/business rule,401authentication,403role/permission/branch,404missing receiptในdetailใหม่,409payload conflict/uniqueness/state conflict คงApiErrorResponse message/fieldsสำหรับvalidation ไม่ remap IllegalArgumentExceptionทั้งระบบ security errorsอาจใช้รูป Spring Security ให้clientอิงHTTP statusด้วย

Testsใหม่เป็นJUnit HTTP/Testcontainers และใช้JWTจริง ได้แก่ CoursePaymentCollectionHttpTest,ReceiptDocumentHttpTest,RefundHttpContractTest ชุดCypressv2เดิมเป็นregressionแยก ไม่ต้องเพิ่มCypressที่ทดสอบสิ่งเดียวกันซ้ำ ผลรันและข้อจำกัดดูรายงานใน docs/testing/2026-11-02-course-payments-implementation.md

ไฟล์implementation: sale/CoursePaymentCollection{Controller,Request,RequestService}.java,ReceiptDocument{Controller,ReadService}.java,RefundController.java Database/serviceหลักเดิมและlegacy SaleControllerไม่เปลี่ยน schemaไม่เปลี่ยน

Frontendส่งต่อ: frontend/src/api/client.ts ต้อง map APIใหม่แยกจากsalesApi; PatientHistoryView/ManagerPatientHistoryView,TransactionReportView,TransactionView และ componentพิมพ์ใช้receiptIdจริงและcoursePayments ส่งIdempotency-Keyคงเดิมเมื่อretryและสร้างใหม่เมื่อผู้ใช้ตั้งใจรับเงินครั้งใหม่ งานbackendนี้ไม่ได้สร้างหน้ารับเงินใหม่หรืออ้างว่าbrowserE2Eของหน้าที่ยังไม่มีผ่านแล้ว