# Appointment Module — Handoff (Frontend + Data)

สรุปทุกอย่างที่ทีม Frontend และ Data ต้องรู้เกี่ยวกับฟีเจอร์ Appointment (APT-1 ถึง APT-6) ตรวจสอบตรงกับซอร์สโค้ดจริงในบรานช์ `feature/appointment-status-brief-v1` (8 ก.ย. 2026)

## 0. สถานะปัจจุบัน

- Branch: `feature/appointment-status-brief-v1` — **ยังไม่ได้ merge เข้า `develop`** และยังไม่ได้เปิด PR
- Backend tests: 116 tests ผ่านทั้งหมด (`mvn clean test`)
- ครอบคลุม: สร้างนัด (APT-1), ค้นหา/ดูรายละเอียด (APT-2), check-in/start-service/complete/no-show/cancel (APT-3/4/5), เลื่อนนัด (APT-6)
- **สถานะนัดหมายครบ 7 ค่าตาม Workflow Brief V1 §4 แล้ว** — ชื่อเดิม `SCHEDULED`/`CHECKED_IN` ถูกเปลี่ยนเป็น `CONFIRMED`/`ARRIVED` (breaking change, migration `V10` ย้ายข้อมูลเดิมให้แล้ว)
- Base URL ตอน dev: `http://localhost:8080` (frontend อ่านจาก env var `VITE_API_BASE_URL`, ดู [frontend/src/api/patients.ts](Sabyeguyz-Clinic_ERP/frontend/src/api/patients.ts) เป็นตัวอย่าง pattern ที่ใช้อยู่)
- Swagger/OpenAPI สด: `GET /v3/api-docs`, UI ที่ `/swagger-ui/index.html`

---

## 1. API Contract (สำหรับ Frontend)

### 1.1 สร้างนัดหมาย

```
POST /api/appointments
Content-Type: application/json

{
  "branchCode": "RP",
  "patientId": 1,
  "therapistId": 1,
  "serviceId": 1,
  "startAt": "2026-09-05T09:00:00+07:00",   // ต้องมี offset เสมอ (ISO-8601)
  "note": "string หรือไม่ส่งก็ได้ (max 500)"
}
```

- `201 Created` + header `Location: /api/appointments/{id}` + body เป็น `AppointmentResponse` (ดู 1.5)
- `400` — ข้อมูลไม่ผ่าน validation, ไม่พบ branch/patient/therapist/service, หรือ record นั้นถูกปิดการใช้งาน (`active=false`), หรือ therapist ไม่ได้สังกัดสาขาที่ระบุ, หรือจองเวลาย้อนหลัง
- `409` — นักกายภาพบำบัดคนนั้นมีนัดอื่นชนช่วงเวลานี้อยู่แล้ว
- `endAt` คำนวณฝั่ง backend เอง = `startAt + service.defaultDurationMinutes` (ไม่ต้องส่งมา และส่งมาก็ไม่มีผล)

### 1.2 ค้นหา/ลิสต์นัดหมาย

```
GET /api/appointments?branchCode=RP&date=2026-09-05&therapistId=1&status=CONFIRMED&page=0&size=20
```

ทุก query param เป็น **optional** ยกเว้น `page`/`size` มี default (`0`/`20`, สูงสุด `size=100`)

- `date` คือวันที่ตามเวลาไทย (`Asia/Bangkok`, ดู §5) กรองด้วย `startAt` ของนัด ไม่ใช่ UTC date
- `status` ต้องเป็นหนึ่งใน `CONFIRMED | ARRIVED | IN_SERVICE | COMPLETED | CANCELLED | RESCHEDULED | NO_SHOW` (ตัวพิมพ์ใหญ่ตรงตัว) ส่งค่าอื่นได้ `400`
- ผลลัพธ์เรียงตาม `startAt` ก่อน แล้วตาม `id` (กันกรณีเวลาเท่ากัน)

Response `200`:
```json
{
  "items": [ /* array ของ AppointmentResponse, ดู 1.5 */ ],
  "page": 0,
  "size": 20,
  "totalItems": 42,
  "totalPages": 3
}
```
(shape เดียวกับ `PatientSearchResult` ที่ frontend ใช้อยู่แล้วกับ `/api/patients`)

### 1.3 ดูนัดหมายรายตัว

```
GET /api/appointments/{id}
```
`200` + `AppointmentResponse` (1.5), หรือ `404` ถ้าไม่พบ

### 1.4 เปลี่ยนสถานะ / เลื่อนนัด

```
POST  /api/appointments/{id}/check-in                 → CONFIRMED หรือ RESCHEDULED → ARRIVED
POST  /api/appointments/{id}/start-service            → ARRIVED → IN_SERVICE
POST  /api/appointments/{id}/complete                 → IN_SERVICE → COMPLETED
POST  /api/appointments/{id}/no-show                  → CONFIRMED หรือ RESCHEDULED → NO_SHOW
POST  /api/appointments/{id}/cancel                   → CONFIRMED, RESCHEDULED หรือ ARRIVED → CANCELLED
      Body: {"reason": "string ไม่ว่าง (max 500)"}
PATCH /api/appointments/{id}                          → เปลี่ยน startAt และ/หรือ therapistId แล้วย้ายเป็น RESCHEDULED
      Body: {"startAt": "2026-09-05T14:00:00+07:00", "therapistId": 2}   // therapistId ไม่ส่ง = ใช้คนเดิม
```

ทุก endpoint คืน `200` + `AppointmentResponse` ที่อัปเดตแล้ว (ไม่ใช่ 204 — ตั้งใจให้ frontend เอา response ไปอัปเดตหน้าจอได้เลยโดยไม่ต้อง GET ซ้ำ)

**State machine** — 7 สถานะตาม Workflow Brief V1 §4 (Confirmed Requirement):

| จากสถานะ | check-in | start-service | complete | no-show | cancel | PATCH (reschedule) |
|---|---|---|---|---|---|---|
| `CONFIRMED` | → `ARRIVED` | `409` | `409` | → `NO_SHOW` | → `CANCELLED` | → `RESCHEDULED` |
| `RESCHEDULED` | → `ARRIVED` | `409` | `409` | → `NO_SHOW` | → `CANCELLED` | → `RESCHEDULED` |
| `ARRIVED` | `409` | → `IN_SERVICE` | `409` | `409` | → `CANCELLED` | `409` |
| `IN_SERVICE` | `409` | `409` | → `COMPLETED` | `409` | `409` | `409` |
| `COMPLETED` | `409` | `409` | `409` | `409` | `409` | `409` |
| `CANCELLED` | `409` | `409` | `409` | `409` | `409` | `409` |
| `NO_SHOW` | `409` | `409` | `409` | `409` | `409` | `409` |

`CANCELLED` และ `NO_SHOW` **คืนช่วงเวลาให้จองใหม่ได้ทันที** — สถานะอื่นทั้งหมดยังกันเวลาไว้ (รวม `RESCHEDULED` ที่ย้ายไปกันเวลาช่วงใหม่แทน) กติกานี้บังคับ 2 ชั้น: JPA overlap check และ PostgreSQL exclusion constraint `excl_appointments_therapist_overlap`

### 1.5 `AppointmentResponse` (shape ที่ endpoint เกือบทั้งหมดคืนกลับ)

```typescript
interface AppointmentResponse {
  id: number
  branchCode: string
  patient: { id: number; hn: string; fullName: string }
  therapist: { id: number; fullName: string }
  service: { id: number; name: string; durationMinutes: number }
  startAt: string   // ISO-8601 UTC เช่น "2026-09-05T02:00:00Z"
  endAt: string      // ISO-8601 UTC
  status: "CONFIRMED" | "ARRIVED" | "IN_SERVICE" | "COMPLETED" | "CANCELLED" | "RESCHEDULED" | "NO_SHOW"
  note: string | null
}
```

### 1.6 รูปแบบ Error (ใช้เหมือนกันทุก endpoint)

```typescript
interface ApiErrorResponse {
  message: string
  fields?: Record<string, string>   // มีเฉพาะตอน 400 จาก @Valid ที่ระบุ field ได้ เช่น {"reason": "must not be blank"}
}
```

| HTTP Status | สถานการณ์ |
|---|---|
| `400` | validation ล้มเหลว / ข้อมูลอ้างอิงไม่ถูกต้อง / JSON parse ไม่ได้ / query param ผิดชนิด |
| `404` | ไม่พบ appointment ตาม id |
| `409` | สถานะปัจจุบันไม่อนุญาตให้ทำ action นี้ หรือช่วงเวลาชนกับนัดอื่น |

`message` เป็นภาษาไทยเสมอ — ใช้แสดงตรงๆ ในหน้าจอได้ ไม่ต้อง map เอง

---

## 2. ข้อควรระวังสำหรับ Frontend

1. **`startAt` ตอนส่ง (request) ต้องมี timezone offset เสมอ** เช่น `+07:00` ไม่ใช่ local time เปล่าๆ — backend ปฏิเสธด้วย `400` ถ้า parse ไม่ได้ (มี hint message บอกรูปแบบที่ถูกต้องอยู่แล้ว)
2. **`startAt`/`endAt` ตอนรับ (response) เป็น UTC** (`Instant`) ต้องแปลงเป็นเวลาไทยเองฝั่ง frontend ก่อนแสดงผล (`+7` ชั่วโมง)
3. **⚠️ `cancelReason` ยังไม่ถูกส่งกลับใน `AppointmentResponse`** แม้จะถูกบันทึกใน DB แล้ว (`cancel_reason` column มีข้อมูลจริง) — ถ้าหน้าจอต้องแสดงเหตุผลการยกเลิก ต้องแจ้ง backend ให้เพิ่ม field นี้ใน DTO ก่อน (จุดแก้อยู่ที่ [AppointmentResponse.java](Sabyeguyz-Clinic_ERP/backend/src/main/java/com/sabyeguyz/clinic/appointment/AppointmentResponse.java) — ง่าย ใช้เวลาไม่นาน)
4. Endpoint เปลี่ยนสถานะทุกตัวเป็น `POST`/`PATCH` ที่ไม่ idempotent อย่างสมบูรณ์ (เรียกซ้ำได้ `409`) — ควร disable ปุ่มหลังกดครั้งแรกกันกดซ้ำ (ตรงกับ UX rule ใน `docs/context.md` §7 "ป้องกันการกด Submit ซ้ำ")
5. ยังไม่มี field ระบุ "ใครเป็นคนกด check-in/cancel" — ยังไม่มีระบบ Authentication ในโปรเจกต์นี้

---

## 3. สำหรับทีม Data — Schema

จาก [V4__appointment.sql](Sabyeguyz-Clinic_ERP/backend/src/main/resources/db/migration/V4__appointment.sql) (Flyway migration, PostgreSQL):

```sql
therapists
  id                  BIGINT PK
  branch_id           BIGINT NOT NULL FK → branches(id)
  code                VARCHAR(20) NOT NULL UNIQUE      -- เช่น "RP-T01"
  full_name           VARCHAR(160) NOT NULL
  active              BOOLEAN NOT NULL DEFAULT TRUE
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()

services
  id                        BIGINT PK
  code                      VARCHAR(20) NOT NULL UNIQUE   -- เช่น "PHYSIO_30"
  name                      VARCHAR(120) NOT NULL
  default_duration_minutes  INTEGER NOT NULL
  active                    BOOLEAN NOT NULL DEFAULT TRUE
  created_at                TIMESTAMPTZ NOT NULL DEFAULT now()

appointments
  id             BIGINT PK
  branch_id      BIGINT NOT NULL FK → branches(id)
  patient_id     BIGINT NOT NULL FK → patients(id)
  therapist_id   BIGINT NOT NULL FK → therapists(id)
  service_id     BIGINT NOT NULL FK → services(id)
  start_at       TIMESTAMPTZ NOT NULL
  end_at         TIMESTAMPTZ NOT NULL
  status         VARCHAR(20) NOT NULL   -- CONFIRMED | ARRIVED | IN_SERVICE | COMPLETED | CANCELLED | RESCHEDULED | NO_SHOW
  note           TEXT NULL
  cancel_reason  TEXT NULL              -- มีค่าเฉพาะแถวที่ status = CANCELLED
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()   -- ทับค่าทุกครั้งที่เปลี่ยนสถานะ/เลื่อนนัด ไม่มีประวัติย้อนหลัง
  CHECK (end_at > start_at)

INDEX idx_appointments_therapist_start (therapist_id, start_at)
INDEX idx_appointments_patient_start   (patient_id, start_at)
INDEX idx_appointments_branch_start    (branch_id, start_at)
```

**สิ่งที่ทีม Data ควรรู้:**

- **ไม่มี soft-delete / delete จริง** — การ "ยกเลิกนัด" คือ `UPDATE status = 'CANCELLED'` ไม่ใช่ลบแถว ตรงกับกฎของทีมใน `docs/context.md` §4 ที่ห้ามลบ transactional record โดยตรง แปลว่า **ทุกแถวใน `appointments` ยังอยู่ครบตลอด** ใช้ทำ report/analytics ย้อนหลังได้โดยไม่ต้องกัง joinกับตารางอื่นเพื่อกู้ข้อมูลที่ถูกลบ
- **ไม่มี audit trail ระดับ field** — รู้แค่ `updated_at` ล่าสุด ไม่รู้ว่าค่าก่อนหน้าคืออะไร หรือใครเปลี่ยน (ยังไม่มี `AuditLog` เชื่อมกับ appointment ในเวอร์ชันนี้ แม้ entity `AuditLog` จะถูกระบุไว้ใน suggested schema ของ `docs/context.md` §6 แล้วก็ตาม — ยังไม่ implement)
- `start_at`/`end_at`/`created_at`/`updated_at` เก็บเป็น UTC (`TIMESTAMPTZ`) เสมอ — ถ้าทำ report ตามวันแบบเวลาไทย ต้อง convert `+7` ก่อน group by (แนวเดียวกับที่ backend ทำใน query filter ของ `GET /api/appointments?date=`)
- Seed data ที่มากับ migration: therapist 2 คน (`RP-T01`, `RP-T02` สังกัดสาขา `RP`), service 3 รายการ (`PHYSIO_30` 30 นาที, `PHYSIO_60` 60 นาที, `ASSESSMENT` 45 นาที) — ใช้เป็น reference data สำหรับ dev/test environment เท่านั้น ของจริงแต่ละสาขาอาจมี therapist/service ชุดอื่น
- Index ทั้ง 3 ตัวออกแบบมาให้ query "นัดของ therapist/patient/branch คนนี้เรียงตามเวลา" เร็ว — ถ้า Data ทำ query แบบอื่น (เช่น filter ตาม `status` อย่างเดียวทั้งตาราง) จะไม่ได้ใช้ index พวกนี้ ควรคุยกับ backend ก่อนถ้าต้อง query หนักๆ แบบอื่น

---

## 4. Business Rules ที่เป็น Assumption (ยังไม่ผ่าน BA ยืนยัน)

ทั้งสองทีมควรรู้ว่าสิ่งเหล่านี้ **ไม่ได้มาจาก requirement ที่ยืนยันแล้ว** — เป็นสมมติฐานที่ backend เลือกเพื่อให้ทำงานต่อได้ก่อน ถ้าเปลี่ยนภายหลังจะกระทบทั้ง state machine และ query filter:

- `COMPLETED`/`CANCELLED` เป็นสถานะปลายทาง เปลี่ยนกลับไม่ได้
- นัดที่ `COMPLETED` ยกเลิกไม่ได้เลย (ต้องใช้กลไก Reversal แยกต่างหากถ้าจำเป็นในอนาคต ยังไม่ implement)
- Reschedule (`PATCH`) แก้ได้แค่เวลา/นักกายภาพบำบัด — เปลี่ยนสาขา/คนไข้/บริการไม่ได้ ต้องยกเลิกแล้วสร้างใหม่แทน
- ยังไม่มีสถานะ `NO_SHOW`
- Reschedule ไม่จำกัดจำนวนครั้ง และไม่เก็บประวัติการเลื่อนแต่ละครั้ง

**Open Questions ที่ต้องถาม BA ก่อน finalize:** ต้องมี `NO_SHOW` ไหม / ยกเลิกนัดที่ `COMPLETED` ต้องทำอย่างไร / ต้องเก็บผู้ดำเนินการ (ใคร check-in/cancel/reschedule) หรือไม่ / จำกัดจำนวนครั้งที่เลื่อนนัดได้ไหม

---

## 5. อ้างอิงซอร์สโค้ด (ตรวจสอบเพิ่มเติมได้)

| เรื่อง | ไฟล์ |
|---|---|
| Controller / endpoint ทั้งหมด | `backend/src/main/java/com/sabyeguyz/clinic/appointment/AppointmentController.java` |
| State machine | `backend/src/main/java/com/sabyeguyz/clinic/appointment/Appointment.java` |
| Response DTO | `backend/src/main/java/com/sabyeguyz/clinic/appointment/AppointmentResponse.java` |
| Error handling | `backend/src/main/java/com/sabyeguyz/clinic/api/ApiExceptionHandler.java` |
| Schema ที่ Appointment API ปัจจุบันใช้งาน | `backend/src/main/resources/db/migration/V4__appointment.sql` |
| Schema ใหม่จากทีมฐานข้อมูล (ยังไม่ใช่ Flyway location ที่เปิดใช้งาน) | `backend/src/main/resources/db/migration_new/V1__initial_schema.sql` |
| ตัวอย่าง fetch client pattern | `frontend/src/api/patients.ts` |

(path ทั้งหมดอยู่ใต้ `Sabyeguyz-Clinic_ERP/` — repo monorepo ปัจจุบัน)
