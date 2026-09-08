# Sales & Payment Module — Handoff (Frontend + Data)

ทุกอย่างที่ทีม Frontend และ Data ต้องรู้เกี่ยวกับการขาย การรับชำระเงิน และการยกเลิกใบเสร็จ
ตรงกับซอร์สในบรานช์ `feature/payment-receipt` (8 ก.ย. 2026)

## 0. สถานะปัจจุบัน

- Branch: `feature/payment-receipt` แตกจาก `develop` — **ยังไม่ได้ merge และยังไม่เปิด PR**
- Backend tests: **175 tests ผ่านทั้งหมด** (`mvn clean test`) เพิ่มจาก 142 ในสไลซ์คอร์ส
- ตรวจกับ PostgreSQL 16 จริงแล้ว: migration ขึ้นถึง `v14`, behaviour + constraint **38/38 ผ่าน**
- ครอบคลุมข้อกำหนด Workflow Brief V1 §6 หัวข้อ **Payment** และ **Cancellation / Void**
- **Migration ใช้เลข `V14` ไม่ใช่ `V13`** เพราะ `V13__course_transfer_audit.sql` ถูกจองไว้แล้วในบรานช์
  `feature/complete-v1-workflows` ที่ยังไม่ merge
- ยังไม่มีหน้าจอ Frontend ในสไลซ์นี้ — backend + API เท่านั้น
- Swagger สด: `GET /v3/api-docs`, UI ที่ `/swagger-ui/index.html` (tag `Sales & Payments`)

---

## 1. หลักคิดสองข้อที่ API นี้ยึด

**1) ราคามาจาก master เท่านั้น** — ตาม brief "คำนวณยอดจาก Service / Course"
Request ไม่มีช่องให้ส่งราคาเข้ามาเลย ถ้าใส่ `unitPrice` หรือ `price` ลงไปใน JSON จะถูกทิ้ง
ระบบอ่านราคาจาก `services.price` / `courses.price` ณ เวลาที่ขาย แล้วเก็บเป็น snapshot ลงใบเสร็จ
แก้ราคาใน master ทีหลังจึงไม่กระทบใบที่ออกไปแล้ว

**2) ไม่มีการลบ** — ตาม brief "Transaction ที่เกิดขึ้นแล้วไม่ควรถูก Delete ทิ้ง"
ยกเลิกใบเสร็จ = เขียนแถวคืนเงินเพิ่ม ไม่ใช่ลบแถวรับเงิน
รายได้ของใบเสร็จ = `SUM(sale_payments.amount)` ซึ่งกลายเป็น 0 หลังยกเลิก โดยที่แถวเดิมยังอยู่ครบ

---

## 2. API Contract

> **เส้นทางทั้งหมดตาม `docs/context.md` §8** — การขายอยู่ใต้ `/api/payments` และการอ่านใบเสร็จอยู่ที่
> `/api/receipts/{id}` (รอบแรกผมตั้งชื่อเป็น `/api/sales` เอง ซึ่งไม่ตรง spec แก้แล้ว)

### 2.1 ขายและรับชำระเงิน

```
POST /api/payments
Content-Type: application/json

{
  "patientId": 1,
  "branchCode": "RP",
  "salespersonStaffId": 4,          // optional — คนละคนกับผู้รักษาได้
  "note": "optional (max 500)",
  "items": [
    { "type": "ASSESSMENT",   "referenceId": 3, "quantity": 1 },   // referenceId = services.id
    { "type": "SINGLE_VISIT", "referenceId": 1, "quantity": 2 },   // referenceId = services.id
    { "type": "COURSE",       "referenceId": 5, "quantity": 1 }    // referenceId = courses.id
  ],
  "payments": [
    { "paymentMethodCode": "CASH", "amount": 10000.00 },
    { "paymentMethodCode": "QR",   "amount": 6200.00 }
  ]
}
```

- `201 Created` + header `Location: /api/receipts/{id}` + body เป็น `SaleResponse` (ดู 2.5)
- **ยอดใน `payments` ต้องรวมได้เท่ากับยอดรวมพอดี** จ่ายขาดหรือจ่ายเกินก็ `400` เท่ากัน
  (ยังไม่รองรับเงินทอน/ค้างชำระ — ดูข้อ 5)
- แบ่งจ่ายหลายช่องทางในใบเดียวได้ ตามที่ส่งมาใน `payments`
- `paymentMethodCode` มาจาก `GET /api/payment-methods` ที่ Admin ตั้งไว้ (seed: `CASH`, `TRANSFER`, `QR`, `CARD`)
- `400` — validation ไม่ผ่าน, ยอดชำระไม่ตรง, ช่องทางชำระเงินไม่มีหรือถูกปิด,
  service/course ถูกปิดใช้งาน, service ยังไม่ได้ตั้งราคา, หรือขายคอร์ส `quantity > 1`
- `404` — ไม่พบ patient / course / salesperson

> **คอร์สต้องขายทีละ 1 ใบต่อบรรทัด** เพราะแต่ละใบมียอดคงเหลือและวันหมดอายุของตัวเอง
> ถ้าลูกค้าซื้อ 2 คอร์ส ให้ส่ง 2 บรรทัด

**ขายคอร์สแล้วสิทธิ์เข้าทันที** — บรรทัด `COURSE` จะสร้าง Course Purchase ให้เลย และเครดิต
`sessions + bonusSessions` เข้าบัญชีคอร์สทันที ผู้ป่วยจึงใช้สิทธิ์ในวันเดียวกับที่ซื้อได้ตาม brief
`id` ของใบซื้อคอร์สอยู่ที่ `items[].coursePurchaseId` เอาไปใช้กับ `POST /api/course-usages` ได้ทันที

### 2.2 ดูใบเสร็จ

```
GET /api/receipts/{id}
```

`200` + `SaleResponse` พร้อม `items` และ `payments` ทั้งหมด เรียงเก่าไปใหม่ · `404` ถ้าไม่พบ

### 2.3 ลิสต์ใบเสร็จ

```
GET /api/payments?patientId=1&branchCode=RP&page=0&size=20
```

ทุก param เป็น optional (`page`/`size` default `0`/`20`, สูงสุด `size=100`) เรียงใหม่ไปเก่า
**รวมใบที่ถูกยกเลิกด้วย** ไม่ซ่อน · `400` ถ้า page/size/สาขาไม่ถูกต้อง · `404` ถ้าไม่พบผู้ป่วย

```jsonc
{ "items": [ { "id": 1, "receiptNo": "RC2601090001", "patient": {…}, "branchCode": "RP",
               "salesperson": null, "total": 15000.00, "paid": 0.00,
               "status": "VOIDED", "soldAt": "…", "voidedAt": "…" } ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1 }
```

หน้าลิสต์เป็น **แบบย่อ ไม่มี `items` และ `payments`** ถ้าต้องการรายละเอียดให้ยิง `/api/receipts/{id}`
รูปแบบ paging เหมือนกับ `GET /api/appointments` ที่มีอยู่แล้ว

### 2.4 ยกเลิกใบเสร็จ

```
POST /api/payments/{id}/reverse
{ "reason": "ลูกค้าขอยกเลิก" }     // บังคับ ห้ามว่าง
```

ระบบจะ:
1. เขียนแถว `REFUND` ยอดติดลบให้ทุกแถว `PAYMENT` → `paid` กลายเป็น `0` **แถวเดิมไม่ถูกลบ**
2. ยกเลิก Course Purchase ทุกใบที่ใบเสร็จนี้ขายไป (ข้ามใบที่ถูกยกเลิกไปก่อนแล้ว)
3. บันทึก `voidReason` + `voidedAt` ลงใบเสร็จ

- `200` + `SaleResponse` ที่ `status = "VOIDED"` · `400` ถ้าไม่ใส่เหตุผล · `404` ไม่พบ · `409` ยกเลิกไปแล้ว
- **ครั้งที่ผู้ป่วยใช้สิทธิ์ไปแล้วจะไม่หายไป** — ซื้อคอร์ส 13 ครั้ง ใช้ 1 แล้วยกเลิกใบเสร็จ
  จะได้ `used = 1`, `remaining = 0` ไม่ใช่ `used = 0`

### 2.5 `SaleResponse`

```jsonc
{
  "id": 1,
  "receiptNo": "RC2601090001",
  "patient":     { "id": 1, "hn": "26010900001", "fullName": "..." },
  "branchCode": "RP",
  "salesperson": { "id": 4, "employeeCode": "SP01", "fullName": "..." },   // null ได้
  "total": 16200.00,        // ยอดที่ต้องชำระ คำนวณจาก master
  "paid":  16200.00,        // เงินที่เหลืออยู่จริงหลังหักคืนเงิน — ใบที่ยกเลิกแล้วเป็น 0
  "status": "PAID",         // PAID | VOIDED
  "note": null,
  "soldAt": "2026-09-08T15:04:05Z",
  "voidedAt": null,
  "voidReason": null,
  "items": [
    { "id": 1, "type": "ASSESSMENT", "description": "ประเมินอาการครั้งแรก",
      "unitPrice": 1200.00, "quantity": 1, "lineTotal": 1200.00, "coursePurchaseId": null },
    { "id": 2, "type": "COURSE", "description": "คอร์สกายภาพ 10 ครั้ง",
      "unitPrice": 15000.00, "quantity": 1, "lineTotal": 15000.00, "coursePurchaseId": 7 }
  ],
  "payments": [
    { "id": 1, "type": "PAYMENT", "paymentMethodCode": "CASH", "amount": 10000.00,
      "reason": null, "reversesPaymentId": null, "createdAt": "..." },
    { "id": 3, "type": "REFUND",  "paymentMethodCode": "CASH", "amount": -10000.00,
      "reason": "ลูกค้าขอยกเลิก", "reversesPaymentId": 1, "createdAt": "..." }
  ]
}
```

### 2.6 เลขที่ใบเสร็จ

`RC` + ปี ค.ศ. 2 หลัก + รหัสสาขา 2 หลัก + เดือน 2 หลัก + running 4 หลัก → `RC2601090001`
เดินแยกตามสาขาและเดือน รีเซ็ตทุกเดือน (กติกาเดียวกับ HN)
Running number ถูกล็อกด้วย row lock บน `branches` ก่อน จึงไม่ซ้ำแม้สอง request ออกใบพร้อมกัน

---

## 3. ตารางใหม่ (สำหรับทีม Data)

Migration `V14__sales_and_payments.sql`

| ตาราง | หน้าที่ |
|---|---|
| `sales` | หัวใบเสร็จ · `receipt_no` unique · `salesperson_staff_id` nullable · `status` PAID/VOIDED |
| `sale_items` | บรรทัดสินค้า · snapshot `description`/`unit_price` · `course_purchase_id` ชี้ใบคอร์สที่สร้าง |
| `sale_payments` | รับเงิน/คืนเงิน **append-only** · `reverses_payment_id` self-FK |
| `receipt_counters` | ตัวนับ running number ต่อสาขาต่อเดือน |

เพิ่มคอลัมน์ `services.price NUMERIC(12,2)` (seed: `ASSESSMENT` 1200, `PHYSIO_30` 700, `PHYSIO_60` 1200)

Constraint ที่ DB บังคับเอง ไม่ได้เช็คแค่ใน Java (ตรวจกับ Postgres จริงแล้ว):

- `uq_sale_payments_reverses` — คืนเงินซ้ำสำหรับการรับเงินรายการเดียวกันไม่ได้
- `chk_sales_voided` — `status = 'VOIDED'` ต้องมี `voided_at` และ `void_reason` เสมอ
- `chk_sale_items_reference` — บรรทัด `COURSE` ต้องมี `course_id`, บรรทัดบริการต้องมี `service_id`

**คิวรีรายได้ต้องรวมทั้งตาราง อย่าอ่านแค่ `sales.total`**
`sales.total` คือยอดที่ *ต้องชำระ* ไม่ใช่รายได้ ใบที่ถูกยกเลิกก็ยังมี `total` เท่าเดิม
รายได้จริงคือ `SUM(sale_payments.amount)` ซึ่งหักคืนเงินไปแล้ว

```sql
-- รายได้ต่อสาขาต่อวัน
SELECT s.branch_id, date(s.sold_at), sum(p.amount)
FROM sales s JOIN sale_payments p ON p.sale_id = s.id
GROUP BY 1, 2;
```

---

## 4. Confirmed Requirement ที่สไลซ์นี้ปิดครบแล้ว

- คำนวณยอดจาก Service / Course ✅
- เลือกช่องทางชำระเงินจากค่าที่ Admin ตั้งไว้ ✅
- ยืนยัน Transaction และบันทึกรายได้ ✅
- Transaction ที่เกิดขึ้นแล้วไม่ถูก Delete ✅
- ผู้ใช้เลือกยกเลิกและระบุเหตุผล ✅
- Reverse ผลกระทบ (รายได้, Course Purchase) ✅
- เก็บเวลาและเหตุผลเพื่อ Audit ย้อนหลัง ✅
- Salesperson เก็บแยกจาก Treating Staff ✅ (`sales.salesperson_staff_id`, ผู้รักษาอยู่ที่ `treatments`)

## 5. ยังไม่ได้ทำ / Open Question

- **เก็บว่า "ใครยกเลิก"** — เก็บแค่ *เมื่อไร* กับ *เพราะอะไร* ยังไม่มี `voided_by`
  เพราะยังไม่มีระบบ auth จริงที่บอกว่าใครกำลังเรียก API (บรานช์ `feature/complete-v1-workflows` กำลังทำ JWT อยู่)
  brief §6 ขอ "เก็บประวัติผู้ยกเลิก" ด้วย → **ต้องตามเก็บให้ครบเมื่อ auth เข้า develop**
- **ยกเลิกบางบรรทัด** — ตอนนี้ยกเลิกได้ทั้งใบเท่านั้น
- **เงินทอน / ค้างชำระ / มัดจำ** — ยอดต้องตรงเป๊ะ ยังไม่รองรับจ่ายบางส่วน
- **ส่วนลด** — ยังไม่มีช่องส่วนลดทั้งใบหรือรายบรรทัด
- **พิมพ์ใบเสร็จ / PDF** — มีแต่ข้อมูล ยังไม่มีเทมเพลต
- **Commission** — `salesperson_staff_id` เก็บไว้พร้อมแล้ว แต่ยังไม่มี Commission Rule / การคำนวณ (brief §7)
- **Course Transfer** ไม่อยู่ในสไลซ์นี้ (อยู่ในบรานช์ `feature/complete-v1-workflows`)
