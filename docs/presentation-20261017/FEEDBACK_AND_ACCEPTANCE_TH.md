# Feedback ล่าสุดและกรณีทดสอบที่ทีมต้องใช้

## แหล่งข้อมูลและขอบเขต

เอกสารนี้สรุป PDF ที่ผู้ใช้ระบุ ใช้เป็น requirement/reference ไม่ใช่คำสั่งให้ agent แก้ทุก module ใน PDF งาน implement รอบนี้เน้น GitHub, CI/CD, Docker, Kubernetes และ test harness บางส่วน

| แหล่งข้อมูล | ใช้เพื่ออะไร |
|---|---|
| `Question_SabyeGuyz_List.pdf` 7 หน้า | คำถามและ annotation เดิม; ประเด็นที่ยังไม่ตอบต้องคงเป็น decision pending |
| `Feedback_SabyeGuyzzzzzzz.pdf` 4 หน้า | Feedback ล่าสุดของหน้าจอ ข้อมูล สถานะ การรับเงินและสิทธิ์ |
| `LA_BALANCE_Commission_Treatment.pdf` 7 หน้า | สูตรค่ามือ/ค่าคอมมิชชัน Tier, visit, close/finalize และ adjustment; อ่านจากภาพทุกหน้า |
| `Lab Week 8 - Kubernetes 101.pdf` 38 หน้า | Concepts และ exercises ของ Pods, probes, volumes, labels, Services |
| `Lab Week 9 - Kubernetes 102.pdf` 30 หน้า | Workloads และงานหน้า 29: own project + friend project, DB, namespaces และ Ingress |

ต้นฉบับอยู่ใน Downloads และ path datapacks ที่ผู้ใช้แจ้ง การสรุปไม่ได้คัดลอกข้อมูลผู้ป่วยในภาพเข้ามาใน repository

## Feedback ที่ต้องส่งต่อเจ้าของ module

| Module | สิ่งที่ feedback ระบุ | สิ่งที่ควรตรวจ/ทดสอบ |
|---|---|---|
| Patients | current มีข้อมูลแล้ว; new มีบริบทนัดหมาย; รองรับต่างชาติและชื่อกลาง; เบอร์/วันเกิดบางบริบทไม่บังคับ | API validation ต้องตรง UI และทดสอบ Thai/foreign, duplicate, optional fields |
| Branch/roles | เลือกสาขาก่อนเข้าใช้งาน; Manager filter ได้ทั้งสองสาขา; ข้อมูลผู้ป่วยค้นได้ข้ามสาขา | แยก patient visibility จาก transaction visibility; สิทธิ์อ้างอิง Figma ที่ทีมยืนยัน |
| Appointments | ชื่อ/นักกายภาพ/เวลาต้องมี *; นัดผู้ป่วยใหม่บังคับชื่อ เบอร์ สาขา; จองข้ามสาขาได้ | Required fields, cross-branch permissions, reschedule/cancel และ status transitions |
| HN | รูปแบบ 9 หลักและตัวอย่างใน PDF | ต้องยืนยันลำดับ ปี/เดือน/สาขา/running เพราะข้อความและตัวอย่างมีความคลุมเครือ; อย่าเดาสูตรจากเลขตัวอย่างเดียว |
| Receipt/payment | เลขเอกสาร `YYMM` + running 4 หลัก เช่น `2609xxxx`; ข้ามเลขที่ยกเลิก; หน่วยเป็นครั้ง; ไม่โชว์ internal item id | Number sequence/reset/branch scope ต้องยืนยัน; cancellation ไม่ใช้เลขซ้ำ |
| Payment | บิลเดียวรับแบบเดียว; เงินสด/โอน/บัตรเครดิต/พร้อมเพย์; สถานะเต็ม/ผ่อน/มัดจำ | สถานะที่แสดงไม่แทนนโยบาย partial-payment ซึ่งต้องยืนยัน API และ accounting behavior |
| Discount | รองรับ % และจำนวนเงิน; ใช้ส่วนลดสองแบบพร้อมกันไม่ได้; ทศนิยม 2 ตำแหน่ง | คิดยอดและ commission base แยก; reject combination; rounding boundaries |
| Course | owner คือคนไข้ แสดง HN และชื่อ; ประวัติใช้/แชร์/โอนครบ; ตั้งวันหมดอายุเป็นจำนวนวันและต่ออายุได้ | จำนวนครั้งและ ledger, expire/extend, participant ownership และ audit |
| Course UI | ตัดช่อง bonus ออกจากหน้าจอตาม feedback | จำนวนครั้งรวมในสูตรล่าสุดยังมีครั้งแถม; ดูข้อขัดแย้งด้านล่าง |
| Refund/tax | feedback ให้คืนเงินนอกระบบ และตัดภาษีจากขอบเขต UI | ถือเป็นขอบเขตที่ลูกค้าแจ้ง ไม่ใช่ข้อวินิจฉัยกฎหมาย; ไม่ implement tax/refund engine จากข้อความนี้ |
| Commission | สิทธิ์แก้ไขมี/ไม่มี; Admin ปิดยอดและบันทึกเหตุผลหลังแก้ | ใช้รายละเอียด close/finalize/adjustment จากเอกสารค่าตอบแทนล่าสุด |
| Dashboard | กราฟเปรียบเทียบ/สรุปยอดขาย, ลูกค้าเก่า/ใหม่, การชำระวันนี้, ต่ออายุ | API aggregate ตรง transactions และ branch filter |
| Google Calendar | สองทาง | เตรียมแผนและ config contract แยก; ยังไม่เชื่อม Google จริงในงาน DevOps รอบนี้ |

ตารางนี้เป็น requirement checklist ไม่ใช่ PASS checklist ต้องมี source SHA, testcase และผลรันของ module ก่อนทำเครื่องหมายว่าผ่าน

## สูตรค่าตอบแทนล่าสุดที่ต้องยึดตาม PDF

ค่ามือ (Treatment Fee) และค่าคอมมิชชันคอร์ส (Course Commission) เป็นคนละรายการ ต้องเก็บและแสดงแยกกัน

| รายการ | สูตร/กฎตาม LA_BALANCE |
|---|---|
| ค่ามือรายครั้งแบบเปอร์เซ็นต์ | `Net Paid ของ Visit หลังส่วนลด × Fee %` |
| ค่ามือรักษาแทนเจ้าของคอร์สแบบเปอร์เซ็นต์ | `(Course Net ÷ Total Sessions รวมครั้งแถม) × Fee %` |
| ค่ามือแบบ fixed | จำนวนบาทต่อครั้งตาม rule ที่ Admin ตั้ง |
| Monthly Tier Sales | ยอดขายคอร์สใหม่ของ PT ในเดือนที่ปิดยอด; visits ของคอร์สเก่าไม่เป็นยอดขายใหม่ |
| Locked Rate | อัตรา Tier ของเดือนขาย ณ Close; ใช้กับคอร์สที่ขายเดือนนั้น |
| Total Course Commission | ราคาคอร์สที่ใช้คำนวณ commission × Locked Rate |
| Commission Per Visit | Total Course Commission ÷ Total Sessions รวมครั้งแถม |
| Owner PT รักษาเอง | รับ Commission Per Visit และหักคอร์ส 1 ครั้ง |
| PT อื่นรักษาแทน | Owner ได้ 0 สำหรับ visit นั้น; PT ที่รักษาได้ค่ามือตาม rule; หักคอร์ส 1 ครั้ง |
| ค่ามือ 100 สูงกว่า commission 70 | จ่ายค่ามือ 100 เต็ม; Owner ได้ 0; ไม่จำกัดค่ามือไว้ที่ commission และไม่จ่ายส่วนที่เหลือให้ Owner |
| Close | คำนวณ Tier และยอดรอจ่าย; ก่อนจ่ายจริง Admin Reopen/Recalculate ได้ |
| Finalize | ยืนยันจ่ายจริงและล็อกงวด; การแก้ภายหลังผ่าน Adjustment งวดถัดไปพร้อมเหตุผล |
| Adjustment | บวก/ลบ payout งวดใหม่; ไม่แก้ยอดงวดเดิม และไม่ใช้หา Tier ใหม่ |

Tier ตั้งค่าได้ ไม่ hard-code 7% ทั้งระบบ ตัวอย่าง `0-50,000 = 5%` และ `50,001-70,000 = 7%` ต้องกำหนดวิธีแบ่งช่วงให้รองรับยอดทศนิยม เช่น 50,000.01 โดยไม่เกิดช่องว่าง

## Acceptance cases จากตัวอย่างจริงในเอกสาร

| ID | Input / เหตุการณ์ | Expected result | เจ้าของงาน |
|---|---|---|---|
| COM-01 | คอร์ส 10,000 บาท / 13 ครั้ง / locked 7% | Pool 700.00; per visit 53.85 ตามตัวอย่างปัดสองตำแหน่ง | Backend commission |
| COM-02 | Owner รักษาเอง 1 visit | Owner 53.85; course remaining ลด 1 | Backend + E2E |
| COM-03 | PT อื่นรักษา, fee 10% | `(10,000/13)*10% = 76.92`; Owner 0; ลด 1 ครั้ง | Backend commission |
| COM-04 | คอร์สขาย ก.ย. ใช้กับ Owner 3 visits ใน ต.ค. | `3*53.85 = 161.55`, ใช้ Tier ก.ย. 7%; ไม่เพิ่ม Tier Sales ต.ค. | Backend commission |
| COM-05 | Reopen เดือน CLOSED ก่อนจ่ายจริง | Recalculate ได้ก่อน Finalize ตามสิทธิ์ | Backend roles/closing |
| COM-06 | Finalized ก.ย. แล้วพบผิด ต้องปรับ -140 ใน ต.ค. | ก.ย. คงเดิม; adjustment ต.ค. -140 ไม่เพิ่ม/ลด Tier Sales | Backend adjustment |
| COM-07 | PT รักษาแทน fee 100 และ commission allocation 70 | Fee 100, Owner 0, ลดคอร์ส 1; Owner ไม่ติดลบ | Backend commission |
| COM-08 | ต.ค. commission 2,500 + treatment fee 500 + adjustment -140 | Net payout 2,860.00 แสดงแต่ละรายการแยก | Backend + frontend |
| COM-09 | ปิดเดือนเดียวกันซ้ำ หรือ import visit เดิมซ้ำ | ไม่จ่ายซ้ำ; ต้องมี identity/idempotency contract ที่ทีมกำหนด | Backend integration |
| COM-10 | ห้ามผู้ไม่มีสิทธิ์ Close/Finalize/Adjust | Reject และไม่แก้ข้อมูล; log ผู้ทำรายการตามสิทธิ์ที่ยืนยัน | Backend security/roles |

COM-01 ถึง COM-08 มาจากสูตร/ตัวอย่าง PDF; COM-09 และ COM-10 เป็นกรณีทดสอบเพิ่มที่เสนอเพื่อคุมการทำงานของระบบ ยังต้องยืนยัน contract และ permission matrix

53.85 × 13 = 700.05 แต่ Pool เป็น 700.00 ตัวอย่าง PDF ใช้ยอดต่อครั้งปัด 2 ตำแหน่ง จึงยังต้องยืนยันวิธีกระจายเศษ 0.05 เมื่อใช้ครบ ไม่ควรอ้างว่าผลรวมตรง Pool โดยอัตโนมัติ

## จุดที่ต้องให้ทีมยืนยันก่อนถือว่าปิด requirement

1. Feedback ให้เอา bonus ออกจาก UI ขณะที่ LA_BALANCE ใช้ Total Sessions รวมครั้งแถม (10+3=13) ต้องแยกการซ่อน field กับการตัดข้อมูลจากสูตร และยืนยันเจ้าของ requirement
2. Commission ใหม่ใช้ Owner 0 เมื่อคนอื่นรักษา ไม่ควรใช้สูตรเก่าที่นำ pool ต่อครั้งลบค่ามือจนอาจติดลบ
3. ราคาที่ใช้หา commission และ Course Net อาจเป็นคนละฐานเมื่อมี discount; ต้องยืนยันรายรายการ/ท้ายบิลและ snapshot fields จากตัวอย่างจริง
4. Shared-course entitlement, การแบ่งเครดิตยอดขายของ PT สองคน, expiry/extend effects และ termination policy ต้องมีคำตอบชัด ไม่ infer จากคำถามที่ยังไม่มีคำตอบ
5. HN/receipt running reset/branch scope, rounding, partial-payment และ POST ที่ timeout ยังต้องมี contract/test evidence
6. สิทธิ์ “ตาม Figma” ต้องให้ทีมระบุ file/version/role matrix ก่อนใช้เป็น gate; งานนี้ไม่มี Figma reference ที่ตรวจได้

## สิ่งที่ infra pipeline ยืนยันได้ในรอบนี้

Pipeline ทดสอบ runtime/authenticated patient flow และ persistence บน schema ของ develop ที่เลือก สถานะ financial acceptance และ COM-01 ถึง COM-10 ต้องบันทึกจาก test ของทีมแยก การผ่าน `/healthz` หรือหน้าเว็บไม่ใช่หลักฐานว่า commission ถูกต้อง
