# Requirements และ Backend backlog ที่ไม่เกี่ยวกับ commission

วันที่ 14 ตุลาคม 2026 (Asia/Bangkok)

## ขอบเขตและฐานงาน

คำขอของนายท่าน: คัด requirement และ todo ที่ไม่เกี่ยวกับ commission แล้ววางแผน implement ต่อจาก branch ปัจจุบัน ไม่ใช่เริ่ม implementation ในรอบนี้

- ฐานที่ตรวจจริง: `feat/carlendar`, HEAD `7462949`; working tree สะอาดก่อนเพิ่มเอกสารนี้
- R = `C:/Users/napal/Downloads/Requirement + Feedback Update.pdf` จำนวน 4 หน้า
- T = `C:/Users/napal/Downloads/Backend-todo.pdf` จำนวน 8 หน้า
- F = `C:/Users/napal/Downloads/Feedback_SabyeGuyzzzzzzz.pdf` จำนวน 4 หน้า; เป็นบันทึก feedback เพิ่มเติมจากที่ R/T สรุปไว้ ไม่ใช่คำสั่งให้ลงมือ implement ทุกบรรทัด
- อ่านครบทั้งสองไฟล์และดูหน้าที่ render แล้ว; เลขข้อด้านล่างเป็นเลขต้นฉบับ
- ข้อความใน PDF เป็นแหล่ง requirement/backlog ไม่ใช่คำสั่งให้เปลี่ยน branch, ลบ legacy, deploy หรือแก้ข้อมูลจริง
- Confirmed Requirement หมายถึงข้อความชัดเจนในเอกสาร ไม่ได้หมายถึงระบบทำครบหรือผ่าน runtime test
- Assumption คือข้อเสนอสำหรับออกแบบ; Open Question ต้องได้รับคำตอบก่อนทำส่วนที่ขึ้นกับคำตอบนั้น
- ข้อ 4 ที่อ้าง Figma ยังตรวจรายละเอียดสิทธิ์ไม่ได้ เพราะไม่มี Figma specification ในไฟล์ที่ให้มา

## เกณฑ์คัด commission ออก

ไม่รวมสูตร/ฐานคำนวณคอมมิชชัน, Tier, Pool, treatment fee/ค่ามือ, override, แบ่งเครดิตขาย, เปลี่ยนผู้รักษาจริงเพื่อคิดค่ามือ, หรือปิดยอดคอมมิชชัน

ข้อที่ปนหลายเรื่องเก็บเฉพาะส่วนที่แยกได้: validation ส่วนลด, สิทธิ์สาขา, วันหมดอายุ, หน้าประวัติคอร์ส, Dashboard ยอดขาย/รับเงิน ส่วนที่ต้องแก้กฎคอมมิชชันเพื่อทำให้สำเร็จจะพักแยก ไม่ถือว่าทำครบทั้งข้อ

## Requirement ที่เก็บไว้

| ID / หน้า | Confirmed Requirement ที่อยู่ใน scope | งาน / เงื่อนไข |
|---|---|---|
| R1–3 / 1 | ปฏิทิน/นัดหมาย/ค่าต่าง ๆ ตามสาขา; Manager ดูและทำข้ามสาขา; พนักงานมีสาขาและเลือกก่อน login; ผู้ป่วยมองเห็นรวมผ่านตัวกรอง | P1; ต้องแยก global patient directory จากข้อมูลกิจกรรมรายสาขา |
| R4 / 1 บางส่วน | สิทธิ์ตาม Figma เฉพาะส่วนไม่เกี่ยวกับ commission | P1; รอ permission matrix/Figma |
| R5 / 1 | อ่านบัตรประชาชนเพื่อกรอกทะเบียน | P2 lookup ฝั่ง backend; โปรแกรมอ่านบัตรฝั่งเครื่องเป็นงานอีกส่วน |
| R6–8 / 1 | คำนำหน้าไทย/อังกฤษ 5 ค่า; เพศหญิง/ชาย/ไม่ระบุ; ชื่อกลาง | P2 |
| R9–10 / 1 | แยก Thai/Foreigner; ต่างชาติไม่บังคับโทร/วันเกิด/พาสปอร์ต; ที่อยู่ไม่บังคับ | P2; เบอร์โทรคนไทยยังขัดกัน ต้องยืนยัน |
| R11 / 1 | ตรวจชื่อซ้ำสำหรับต่างชาติที่ไม่มีเลขบัตร | P2; ยังไม่ระบุว่าต้องห้ามหรือเตือน |
| R12 / 1 | New เฉพาะคนมีนัด; Current มีข้อมูลครบ | P3; ต้องนิยามข้อมูลครบตาม P2 |
| R13 / 1 | ประวัติใบเสร็จค่ารักษาและคอร์สในผู้ป่วย | P5 |
| R14 / 1 | HN 9 หลัก ตัวอย่าง 261201004 | P6; รูปแบบและ capacity ต้องยืนยันกับ DB |
| R15–17 / 1–2 | นัดรายครั้ง/รายคอร์ส; ระบุช่องบังคับ; ชื่อ/นักกายภาพ/เวลา; ตัดห้อง; ใช้สาขาที่ login; New/Current และเบอร์โทร | P3; auto-fill สาขาเป็น Assumption |
| R18–20 / 2 | New นัดด้วยชื่อจริงหรือชื่อเล่น+โทร+สาขา; ปฏิทินมีเบอร์; ค้นชื่อ/โทร; ตัดตัวกรองบริการทั้งหมด | P3 |
| R21–22 / 2 | ยังไม่ยืนยัน/ยืนยัน/ยกเลิก/ใช้บริการแล้ว; New ยืนยันแล้วไปลงทะเบียน; Current ไปบริการ | P3; เหตุการณ์ที่ทำให้ใช้บริการแล้วต้องยืนยัน |
| R23 / 2 | Google Calendar สองทางและตารางว่าง/ไม่ว่าง | P8; รอบแยกหลังยืนยันบัญชีและ sync policy |
| R24–25 / 2 | จากนัดไปบริการของคนเดิม; เหลือ Single Visit/Course | P3/P5; ไม่ลบ historical Assessment |
| R26 / 2 บางส่วน | ส่วนลด % หรือจำนวนเงิน ใช้สองแบบพร้อมกันไม่ได้ ทศนิยม 2 ตำแหน่ง และข้อความไม่จำกัดส่วนลด | P4; ไม่เปลี่ยน commission base; ความหมายไม่จำกัดต้องยืนยัน |
| R28 / 2 | ชำระแล้วเปิดหน้าพิมพ์ใบเสร็จทันที | P5 |
| R29 / 2 | เลขที่เอกสาร YYMM+รัน 4 หลัก; ไม่ reuse เลขยกเลิก; ไม่โชว์รหัสรายการ; หน่วยครั้ง | P6/P5; ไม่เปลี่ยน quantity/price semantics ของคอร์สโดยอนุมาน |
| R30 / 2 | สถานะชำระเต็ม/ผ่อน/มัดจำ; ตัดแต้ม/วงเงินคงเหลือ | P4; เป็น label ไม่ใช่เพิ่มระบบสินเชื่อ |
| R31 / 2 | บิลหนึ่งวิธี; เงินสด/โอน/เครดิต/พร้อมเพย์ | P4 |
| R32 / 2 | ตัดภาษีออก | P5; เป็นนโยบายจากเอกสาร ไม่ใช่ข้อสรุปกฎหมายใหม่ |
| R33 / 2 | ตัดคืนเงินหลังใช้คอร์ส; คืนเงินนอกระบบ | P4 guard; ไม่เหมารวมยกเลิกนัด/void บิลว่าเป็น refund |
| R35–36 / 3 | อายุคอร์สเป็นวันตั้งตอนตั้งค่า; ต่ออายุ/แก้ expiry; จำนวนวันที่เหลือ | P7 |
| R38 / 3 | ใบตัดคอร์สมี HN/ชื่อเจ้าของ/ชื่อนักกายภาพ; ประวัติใช้/โอนทั้งหมด | P7; แสดงข้อมูลที่บันทึกแล้ว ไม่เปลี่ยนการเลือกผู้รักษา |
| R39 / 3 | โอนโดยค้นชื่อลูกค้าปลายทาง ไม่เลือกนักกายภาพ | P7 เฉพาะ UX โอน; ไม่เปลี่ยน owner/credit/ค่ามือ |
| R40 / 3 บางส่วน | สิทธิ์ของ B อ้างวันหมดอายุคอร์สต้นทาง A | P7; ส่วน B เลือกผู้รักษาตอนใช้พักออก |
| R43 / 3 | Dashboard ยอดขาย/กราฟ/เก่าใหม่/รับเงินวันนี้/ต่ออายุ/อัปเดตแพ็กเกจ | P8; แยกยอดขายจากเงินรับ และนิยามเก่าใหม่ก่อน |
| F3 / 3 เพิ่มจาก R/T | หน้าเพิ่มคอร์สในข้อมูลผู้ป่วยให้แนบรูปจากภาพถ่ายได้ | P7 เป็นงานแยก; ต้องกำหนดรูปอะไร ผูกกับผู้ป่วยหรือรายการซื้อคอร์ส และสิทธิ์เข้าถึง/จัดเก็บก่อนสร้าง API |

## Requirement ที่พักออก

| ID / หน้า | เหตุผล |
|---|---|
| R4 ส่วนสิทธิ์แก้ commission / 1 | ตรงกับ commission |
| R26 ส่วนฐานคิดคอมก่อน/หลังส่วนลด / 2 | ห้ามเปลี่ยน commission calculation ใน scope นี้ |
| R27 / 2 | แสดงและแก้ค่าคอมหมอ |
| R34 / 2 | แก้หลังปิดเดือนและ Admin ปิดยอด; ผูกกับ monthly commission closing ใน todo T12; หากต้องการปิดยอดบัญชีทั่วไปต้องแยก requirement ใหม่ |
| R37 / 3 + F2 / 2 | F ระบุชัดว่า “ไม่เอา bonus ตัดออกหมด” ขณะที่ R/T ยังตั้งข้อสงสัยเรื่องผลต่อ Pool; พักการเปลี่ยน entitlement/Pool ทั้งข้อเพราะกระทบ commission; อย่าเพียงซ่อน UI แล้วอ้างว่าทำครบ |
| R40 ส่วนเลือกผู้รักษาตอนใช้ / 3 | ผูกกับค่ามือ |
| R41 / 3 | แชร์หมอ/เปลี่ยนผู้รักษา/แบ่งเครดิตขาย |
| R42 / 3 | นิยาม transaction ที่รวมค่ามือ ไม่อยู่ในงานนี้ |

## Todo ต้นฉบับ: ครบทั้ง 31 ข้อ

| T / หน้า | ผลคัดและแผน |
|---|---|
| 1 / 1 | พัก: treatingStaffId และค่ามือ |
| 2 / 1 | พัก: แชร์เครดิตขายและผลย้อนหลัง |
| 3 / 2 | พัก: Bonus/Pool |
| 4 / 2 | เก็บ P1: branch authorization; ไม่รวม commission permission |
| 5 / 2–3 | เก็บ P7: expiry/extension/remaining days/transfer |
| 6 / 3 | เก็บ P4: หนึ่งวิธีต่อบิล |
| 7 / 3 | เก็บ P4: payment label; schema มี enum แล้ว ไม่เพิ่ม boolean/enum ซ้ำ |
| 8 / 3 | เก็บบางส่วน P4: validation/precision ส่วนลด; ไม่แก้ฐานคิดคอม |
| 9 / 3 | เก็บ P4: display พร้อมเพย์; backend canonical PromptPay มีแล้ว |
| 10 / 4 | เก็บ P4: ปิด/จำกัด refund หลังใช้ โดย guard แยกได้ |
| 11 / 4 | เก็บ P5: ภาษี 0 และแก้คอมเมนต์นโยบายที่ค้าง |
| 12 / 4 | พัก: monthly closing/commission override |
| 13 / 4–5 | เก็บ P6: HN/receipt identifier หลังยืนยัน schema/รูปแบบ |
| 14 / 5 | ตรวจซ้ำ P6: schema ปัจจุบันใช้ patient_hn แล้วและไม่พบ chk_patient_hn เดิม; ไม่แก้ branch ของบุคคลอื่น |
| 15 / 5 | เก็บ P2: validation/ชื่อกลาง; DB มีชื่อกลางแล้ว แต่ Java/API ยังขาด |
| 16 / 5 | เก็บ P2: title/gender contract และ UI |
| 17 / 5 | เก็บ P3: New→register→Current |
| 18 / 6 | เก็บ P3: new appointment validation/ไม่ใช้ห้อง |
| 19 / 6 | เก็บ P3: note/cancelReason/endAt; duration/endAt ต้องยืนยันความหมาย |
| 20 / 6 | เก็บ P2: exact national-ID lookup; ไม่สร้าง card-reader daemon ใน backend |
| 21 / 6–7 | เก็บ P8: Dashboard ที่ไม่ใช่รายงาน commission |
| 22 / 7 | พัก: หน้าคอมมิชชันรายคอร์ส/Tier |
| 23 / 7 | เก็บเป็นงานมีเงื่อนไข P8: Google Calendar สองทาง |
| 24 / 7 | เก็บ P1: home branch กับสิทธิ์หลายสาขาเป็นคนละเรื่อง; รอเลือก contract |
| 25 / 7 | เก็บ backlog P8: room ไม่ว่าง/ซ่อม; ไม่จำเป็นต่อฟอร์มนัดที่เอาห้องออก |
| 26 / 7 | เก็บ backlog P8: email login/password reset; ยังไม่มี email ใน App_user |
| 27 / 7 | เก็บ backlog P2: allergy/chronic/consent/emergency; ต้องกำหนดการเก็บและ migration ก่อน |
| 28 / 7 | เก็บ P9: migration dry run หลัง P2/P6 ตัดสินแล้ว |
| 29 / 8 | เก็บ P9: วินิจฉัย Maven dependency/plugin download จาก log จริง ไม่สรุปว่าเป็น product bug |
| 30 / 8 | เก็บทุก P: API/browser test และ api-v2-frontend-contract.md |
| 31 / 8 | ตรวจซ้ำ P9: main class/Frontend E2E CI ใช้ v2 แล้ว; ไม่ cutover ซ้ำและไม่ลบ legacy อัตโนมัติ |

ผลคัด todo: พักทั้งข้อ 5 รายการ (1, 2, 3, 12, 22); เก็บ 26 รายการ โดย T8 เก็บเฉพาะส่วนไม่ใช่ commission และ T31 เป็น verification/cleanup assessment

## หลักฐานใน branch ปัจจุบัน: static inspection

| จุดตรวจ | สิ่งที่พบ | ผลต่อแผน |
|---|---|---|
| backend/pom.xml, application.properties, application-v2.properties | start-class เป็น ClinicV2Application, Flyway db/migration, Hibernate validate | ไม่ใช้แผนเก่าที่รอเปิด v2 |
| .github/workflows/frontend-e2e-v2.yml | ใช้ profile cypress-v2 | ตรวจ flow ที่ขยาย ไม่เปลี่ยน CI runtime ซ้ำ |
| docs/testing/2026-10-13-Phase-2-V2-Acceptance.md | บันทึก 143 API tests และ runtime/schema checks; แยกข้อจำกัดซื้อคอร์สกับจ่ายเงินจริง | เป็นผลเก่าใน repo ไม่ใช่การรันใหม่วันที่ 14 |
| migration/V1__initial_schema.sql | patient_middle_name มี; patient_hn VARCHAR(10) และ comment เสนอ YYBBMMNNN; payment_label enum มี; receipt constraint RE+8 digits | schema และ HnGeneratorService ยังไม่ตรงกัน; ทำ forward migration เมื่อจำเป็น |
| Patient.java, PatientRegistration.java, PatientController.java | ไม่พบ mapping ชื่อกลางใน entity/DTO ที่ตรวจ; RegisterRequest ใช้ customerType แต่ response แยก customerTypeCode | เพิ่ม end-to-end mapping; อย่าสับสน customer group กับ Thai/Foreigner |
| AppointmentController.java | endAt=startAt; note/cancelReason=null; note กับ reschedule therapistId ถูกละเลย; query ไม่มีค้นชื่อ/โทร | ยังขาด persistence/search/contract แม้ flow พื้นฐานมีแล้ว |
| PaymentTransactionService.java | รับหลาย payment ได้; refreshPaymentLabel เดาจากยอดเงิน และ 0 เป็น Deposit | ปรับ validation และ explicit label policy; ต้องนิยามมัดจำกับผ่อน |
| V1 และ course/patient package | พบ `document_snapshot` สำหรับใบตัดคอร์ส แต่ไม่พบตาราง/endpoint สำหรับภาพแนบคอร์สในการตรวจแบบ static | F3 เป็น feature ใหม่ที่ต้องมี storage contract; อย่าใช้ document_snapshot เป็นที่เก็บภาพโดยเดา |
| PaymentMethod.java | qr/promptpay canonicalize เป็น PromptPay | เน้น UI/contract/regression ไม่สร้าง method ใหม่ซ้ำ |
| Discount.java | จำกัด %≤100 และ Fixed≤price | ขัดกับคำไม่จำกัด; รอคำตอบก่อนลบ guard |
| ReceiptService.java | ใช้ REYYMMNNNN; ภาษี 0; คอมเมนต์ยังรอยืนยัน | ตัวอย่างใน todo เก่าไม่ตรงโค้ดล่าสุด |
| CourseTransferService.java | โอน entitlement บน Course เดิม ไม่สร้าง Course ใหม่ | พิสูจน์ expiry inheritance ด้วย test; ไม่สร้างล็อตซ้ำ |
| catalog/Course.java และ V1 | validityValue/unit และ expiryDate; schema service ใช้ default_validity_value/unit | ไม่อิงชื่อ default_validity_days ใน todo โดยไม่ตรวจ |

ยังไม่ได้รัน backend, browser, build หรือ test ในรอบวางแผนนี้

## Open Questions ที่เป็น gate เฉพาะงาน

| Q | คำตอบที่จำเป็น | งานที่รอ |
|---|---|---|
| Q1 | F3 บอกว่าเบอร์โทรและวันเกิดไม่บังคับในการลงทะเบียน แต่ R ฉบับเดิมบังคับเบอร์คนไทย; ยืนยันว่าคำตอบล่าสุดใช้กับทุกสัญชาติหรือเฉพาะต่างชาติ และ field ใดทำให้ Current | validation ส่วนที่ขัดกัน P2/P3; เบอร์โทรสำหรับ NEW appointment ยังบังคับตาม F3/R18 |
| Q2 | foreigner ชื่อเหมือนกัน: เตือนหรือห้าม; normalize แบบใด | duplicate-by-name P2 |
| Q3 | HN แบ่ง 9 หลักอย่างไร ตัวอย่าง 261201004 จะแยก year/branch/run/month แบบใด; capacity/reset/legacy policy | P6; ห้ามใช้สูตรจาก comment แทนคำตอบ |
| Q4 | receipt sequence ต่อสาขาหรือรวม; ไม่แสดง RE หรือเปลี่ยน persisted ID; หน่วยครั้งรวมคอร์สอย่างไร | P6/บางส่วน P5 |
| Q5 | ไม่จำกัดส่วนลดหมายถึงไม่มีกำหนดเพดานธุรกิจแต่สุทธิ≥0 หรืออนุญาตเกินราคา/เกิน100%; ปัดหรือ reject >2 ตำแหน่ง | validation P4 |
| Q6 | บิลเดียวหนึ่งวิธีอนุญาตหลายงวดด้วยวิธีเดิมไหม; Deposit กับ Installment ต้องเลือกตอนออกบิลหรือ derive; ยอด 0 label ใด | payment label/งวด P4 |
| Q7 | expiry รวมวันหมดอายุไหม; ใครแก้ได้; ต่อจากวันเดิมหรือวันนี้; ต่ออายุหนึ่ง Course แล้วกระทบสมาชิกที่รับโอนทั้งหมดหรือไม่ | extension P7 |
| Q8 | เวลาสิ้นสุด/duration มาจากนัดหรือ catalog; ใช้บริการแล้วผูก complete หรือ receipt; แก้ therapist ในการเลื่อนนัดได้ไหม | timing/status P3 |
| Q9 | Figma permission matrix, Manager multi-branch selection, Admin ไม่ได้รับ cross-branch โดยอัตโนมัติใช่ไหม | permission gap P1 |
| Q10 | ข้อมูล allergy/consent/emergency จะเก็บ field ใดและเก็บข้อมูลเดิมอย่างไร | T27/P2/P9 |
| Q11 | Dashboard เก่า/ใหม่ตาม first visit หรือ registration; ยอดขายตาม saleDate หรือเงินรับ; ต่ออายุ/อัปเดตแพ็กเกจเป็น metric หรือปุ่ม | P8 |
| Q12 | Google owner account/calendar per branch/OAuth consent/conflict/delete/privacy policy; rooms และ password-reset delivery channel | backlog P8 |
| Q13 | รูปที่แนบตอนเพิ่มคอร์สคือหลักฐานการซื้อ/ภาพผู้ป่วย/เอกสารชนิดใด; ผูกกับ purchase หรือ profile; รับชนิดไฟล์/ขนาด/จำนวนเท่าไร; ใครเห็น ลบ หรือเก็บนานเท่าไร | attachment P7; ห้ามเก็บภาพลง JSON snapshot ของใบตัดคอร์สโดยไม่มี contract |

Assumption ที่เสนอ: นัดใช้สาขาปัจจุบันของ session และซ่อน selector สำหรับ non-Manager; expiry เป็น LocalDate ใน Asia/Bangkok; IDs เดิมคงไว้ย้อนหลัง; หนึ่งวิธีต่อบิลไม่เท่ากับหนึ่ง payment record ตลอดอายุบิล ต้องยืนยัน Q6

งานที่ชัดสามารถเริ่มได้โดยไม่รอ Q ทั้งหมด: mapping ชื่อกลาง/คำนำหน้า/เพศ, exact ID lookup, search นัด, persist note/reason, PromptPay display, ภาษี/ประวัติ/print, และ transfer expiry regression ส่วนที่ไม่เปลี่ยนสิทธิ์

## สิ่งที่ F เพิ่มหรือยืนยันหลังแผนฉบับแรก

- F1 / หน้า 1: Payment “แค่บันทึก” และแยก Sale transaction เป็นยอดขายร้าน; สอดคล้องกับการแยก saleDate ออกจากวันที่รับเงินใน P4/P8 ส่วน Treatment transaction เป็นค่ามือจึงยังพัก
- F2 / หน้า 2: ทุกคนเห็นข้อมูลผู้ป่วยทั้งสองสาขาผ่านตัวกรอง ขณะที่ข้อมูลกิจกรรม/ค่าต่าง ๆ จำกัดตามสาขา; P1 ต้องทดสอบทั้งสองด้าน การนัดข้ามสาขายังให้เฉพาะ Manager ตาม R2
- F2 / หน้า 2: ตัวอย่าง HN 261201004 แต่คำอธิบายปี/สาขา/รัน/เดือนในบรรทัดเดียวกันยังแยก 9 หลักไม่ลงตัว; Q3 ยังเปิดอยู่
- F2 / หน้า 2: Bonus “ตัดออกหมด” ทำให้ข้อความว่าตัดเฉพาะหน้าจอไม่น่าพอ แต่ยังไม่อนุญาตให้เปลี่ยนสูตร Pool ใน scope ไม่เกี่ยว commission
- F3 / หน้า 3: รูปแนบตอนเพิ่มคอร์สเป็น requirement ที่ R/T ไม่ได้ระบุ; เพิ่ม Q13 และงาน storage/permission แยกใน P7
- F3 / หน้า 3: เบอร์โทร/วันเกิดที่ลงทะเบียน “ไม่บังคับ” แต่ยังขัดกับ R10 ที่ระบุเงื่อนไขคนไทย; Q1 ยังต้องยืนยันผู้ใช้ที่ครอบคลุม
- F3 / หน้า 3: “ข้ามเลขใบเสร็จที่ยกเลิก” สนับสนุน test ว่าเลข void แล้วไม่ reuse; ไม่ตอบว่ารันเลขรวมทุกสาขาหรือแยกสาขา

## Q11 ยืนยันเพิ่มเติมโดยนายท่าน วันที่ 15 ตุลาคม 2026

- ลูกค้าใหม่ยึดวันลงทะเบียน: ผู้ลงทะเบียนในช่วงที่เลือกนับแม้ยังไม่มาใช้บริการ.
- ลูกค้าเก่า: ผู้ลงทะเบียนก่อนช่วงและมาใช้บริการในช่วงที่เลือก; นับคนไม่ซ้ำ. นายท่านยืนยันนิยามช่วงเวลานี้โดยตรง.
- ต่ออายุ/อัปเดตแพ็กเกจบน Dashboard เป็นตัวเลขสรุปจำนวนรายการ.
- อัปเดตแพ็กเกจหมายถึงแก้แม่แบบคอร์สใน Back Office ไม่ใช่เปลี่ยนแพ็กเกจที่ผู้ป่วยซื้อ.
- implementation/field contract และขอบเขตประวัติที่บันทึกใหม่ดู `docs/api-v2-frontend-contract.md` หัวข้อ P8 Dashboard Q11. Q12 และคำถามของ P6/P7/P9 ยังไม่ถูกปิดจากคำตอบ Q11.
