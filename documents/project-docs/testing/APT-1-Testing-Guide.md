# คู่มือการทดสอบ APT-1: Create Appointment

เอกสารนี้อธิบายวิธีทดสอบฟีเจอร์ **APT-1 — สร้างนัดหมาย (`POST /api/appointments`)** ตั้งแต่ Automated Test
จนถึงการยิง API จริงบน PostgreSQL เพื่อใช้ตรวจงานก่อนเปิด Pull Request และใช้เป็นหลักฐานประกอบการส่งงาน

อัปเดตล่าสุด: 29 สิงหาคม 2026

---

## 1. ขอบเขตของ APT-1

| หัวข้อ | รายละเอียด |
|---|---|
| Endpoint | `POST /api/appointments` |
| ผลลัพธ์ | สร้างนัดหมายสถานะ `CONFIRMED` |
| Business Rule หลัก | Therapist คนเดียวกันห้ามมีนัดทับช่วงเวลากัน |
| ยังไม่อยู่ในขอบเขต | `GET`, `PATCH`, check-in, complete, cancel (อยู่ใน APT-2 ถึง APT-6) |

Requirement อ้างอิงจาก [context.md](docs/context.md) หัวข้อ Appointment และ Integration Test Matrix
ใน [docs/testing/README.md](docs/testing/README.md)

---

## 2. สิ่งที่ต้องเตรียม

| เครื่องมือ | ใช้ตอนไหน |
|---|---|
| JDK 17 ขึ้นไป | รัน Test และ Build |
| Maven | รัน Test และ Build |
| Docker Desktop | ทดสอบกับ PostgreSQL จริง (Part B) |
| Postman | ทดสอบ API ด้วยมือ (Part B ทางเลือกที่ 1) |
| curl | ทดสอบ API ด้วยมือ (Part B ทางเลือกที่ 2) |

### ตั้งค่า Toolchain บนเครื่องที่ใช้พัฒนา

สคริปต์ `backend\run-tests.ps1` มองหา JDK ที่ `C:\Program Files\Java\jdk-17` หรือ `jdk-25`
ถ้าเครื่องไม่มี JDK ที่ path นั้นจะขึ้น `No supported JDK was found` ให้ตั้งค่าเองแทน

```powershell
$env:JAVA_HOME='C:\Users\<Your Username>\.jdks\corretto-21.0.12'
$env:Path="$env:JAVA_HOME\bin;D:\Program Files\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin;$env:Path"
```

ทั้ง JDK และ Maven ชุดนี้ติดมากับ IntelliJ ตรวจสอบว่าใช้งานได้ด้วย

```powershell
java -version
mvn -version
```

> **หมายเหตุสำหรับ CI:** GitHub Actions runner ไม่มี IntelliJ ดังนั้น Workflow ต้องใช้
> `actions/setup-java` กำหนด JDK เอง ห้ามอ้างอิง path ในเครื่องนักพัฒนา

---

## 3. โครงสร้างการทดสอบ 3 ชั้น

APT-1 ถูกทดสอบแยกเป็น 3 ระดับ แต่ละระดับพิสูจน์คนละเรื่องกัน

| ระดับ | ไฟล์ | จำนวน | พิสูจน์ว่า |
|---|---|---|---|
| Repository | `AppointmentRepositoryTest` | 7 | JPQL ตรวจการทับเวลาถูกต้องทุกรูปแบบ |
| Service | `AppointmentBookingServiceTest` | 7 | Business Rule ครบและ throw Exception ถูกชนิด |
| API | `AppointmentApiTest` | 7 | Exception แปลงเป็น HTTP Status Code ถูกต้อง และ JSON ตรง Contract |

ทั้งหมดอยู่ใน `backend\src\test\java\com\sabyeguyz\clinic\appointment\`

### ทำไมต้องแยก 3 ชั้น

ถ้าเขียนรวมชั้นเดียว เวลา Test แดงจะแยกไม่ออกว่าปัญหาอยู่ที่ SQL, req หรือการแปลง Error
การแยกชั้นทำให้ตำแหน่งของบั๊กชัดเจนตั้งแต่ชื่อไฟล์ที่แดง

ตัวอย่างที่เห็นภาพ: ถ้ามีคนลบ `@ExceptionHandler(AppointmentConflictException.class)` ทิ้ง
Test ชั้น Repository และ Service จะยังเขียวทั้งหมด มีแต่ `AppointmentApiTest` ที่จับได้ว่ากลายเป็น 500

---

## Part A — Automated Test

### A.1 รัน Test ทั้งหมด

```powershell
cd ./backend
mvn -B -f pom.xml test
```

ผลที่ต้องได้

```text
Tests run: 7,  Failures: 0 -- in com.sabyeguyz.clinic.appointment.AppointmentApiTest
Tests run: 7,  Failures: 0 -- in com.sabyeguyz.clinic.appointment.AppointmentBookingServiceTest
Tests run: 7,  Failures: 0 -- in com.sabyeguyz.clinic.appointment.AppointmentRepositoryTest
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**40 tests = 21 ของ Appointment + 19 ของ Patient ที่มีอยู่เดิม** ตัวเลข Patient ต้องไม่ลดลง
ถ้าลดแปลว่า APT-1 ไปทำของเดิมพัง

### A.2 รันก่อนเปิด Pull Request

```powershell
mvn -B -f pom.xml verify
```

ต้องใช้ `verify` ไม่ใช่ `test` เพราะ CI รัน `verify` ซึ่งรวมขั้นตอน package เป็น jar ด้วย
ผลที่ต้องได้เพิ่มมาคือ

```text
Building jar: ...\target\clinic-erp-backend-0.0.1-SNAPSHOT.jar
BUILD SUCCESS
```

### A.3 รันเฉพาะบางคลาสตอนแก้บั๊ก

```powershell
mvn -f  pom.xml test -Dtest=AppointmentRepositoryTest
mvn -f  pom.xml test -Dtest=AppointmentApiTest #rejectsOverlappingAppointmentWithConflict
```

### A.4 Test แต่ละตัวพิสูจน์อะไรบ้าง

**`AppointmentRepositoryTest` — ตรรกะการทับเวลา**

นัดตั้งต้นคือ 09:00–10:00 ของ Therapist คนที่หนึ่ง

| Test | ช่วงเวลาที่ขอจอง | ผล | เหตุผล |
|---|---|---|---|
| `detectsPartialOverlap` | 09:30–10:30 | ทับ | ทับบางส่วน |
| `detectsFullyContainedAppointment` | 09:15–09:45 | ทับ | อยู่ข้างในทั้งหมด |
| `detectsEnclosingAppointment` | 08:00–11:00 | ทับ | ครอบนัดเดิม |
| `allowsBackToBackAppointmentAfter` | 10:00–11:00 | **ไม่ทับ** | ติดกันพอดี |
| `allowsBackToBackAppointmentBefore` | 08:00–09:00 | **ไม่ทับ** | ติดกันพอดี |
| `allowsSameTimeForAnotherTherapist` | 09:00–10:00 คนละคน | ไม่ทับ | คนละ Therapist |
| `ignoresCancelledAppointments` | ทับนัดที่ `CANCELLED` | ไม่ทับ | นัดที่ยกเลิกไม่กินเวลา |

สองแถวที่เป็นตัวหนาสำคัญที่สุด เพราะเป็นตัวคุมว่าเงื่อนไขต้องเป็น `<` และ `>`
ไม่ใช่ `<=` และ `>=` ซึ่งจะทำให้คนไข้จองต่อกันไม่ได้เลย

**`AppointmentBookingServiceTest` — req**

| Test | พิสูจน์ |
|---|---|
| `createsConfirmedAppointmentUsingServiceDuration` | สร้างสำเร็จ, `CONFIRMED`, `endAt` คำนวณจาก duration ของ Service |
| `rejectsOverlappingAppointmentForSameTherapist` | ห้ามจองเวลาซ้ำ |
| `allowsBackToBackAppointmentForSameTherapist` | นัดติดกันจองได้ทั้ง Pipeline |
| `rejectsStartTimeInThePast` | ห้ามจองย้อนหลัง |
| `rejectsUnknownPatient` | id ที่ไม่มีจริงถูกปฏิเสธ |
| `rejectsInactiveService` | ข้อมูล Master ที่ปิดใช้งานถูกกัน |
| `rejectsTherapistFromAnotherBranch` | Therapist ต้องสังกัดสาขาที่เลือก |

**`AppointmentApiTest` — สัญญากับ Frontend**

| Test | Status ที่คาด |
|---|---|
| `createsAppointment` | 201 + `Location` header |
| `rejectsOverlappingAppointmentWithConflict` | 409 |
| `allowsBackToBackAppointment` | 201 |
| `rejectsMissingPatientId` | 400 พร้อม `fields.patientId` |
| `rejectsUnknownTherapistWithBadRequest` | 400 |
| `rejectsMalformedStartAt` | 400 |
| `documentsAppointmentContract` | OpenAPI มี tag `Appointments` และ schema ครบ |

---

## Part B — ทดสอบบน PostgreSQL จริง

Automated Test ทั้งหมดรันบน **H2** ซึ่งเป็นคนละฐานข้อมูลกับ Production
ตาม [context.md](docs/context.md) ข้อ 17.10 ห้ามสรุปว่าระบบใช้งานได้เพียงเพราะ Build ผ่าน
จึงต้องมีขั้นตอนนี้ทุกครั้งก่อนเริ่มทำ feature ถัดไป

### B.1 เปิดฐานข้อมูลและ Backend

```powershell
cd ./deploy
docker compose -f docker-compose.yml up -d postgres

cd ./backend
mvn -f pom.xml spring-boot:run
```

ตรวจสอบว่าพร้อมใช้งาน

```bash
curl http://localhost:8080/actuator/health      # {"status":"UP"}
```

ตรวจสอบว่า Backend ที่รันอยู่เป็นโค้ดล่าสุดจริง ไม่ใช่ Process เก่าที่ค้างจากการรันรอบก่อน

```bash
curl -s http://localhost:8080/v3/api-docs | findstr /C:"Appointments"
```

### B.2 ข้อมูลตั้งต้นที่ Flyway สร้างให้

Migration `V4__appointment.sql` ใส่ข้อมูลเหล่านี้ให้อัตโนมัติ

**Branch**

| id | code | ชื่อ |
|---|---|---|
| 1 | `RP` | สาขาราชพฤกษ์ |

**Therapist** (สังกัดสาขา RP ทั้งคู่)

| id | code | ชื่อ |
|---|---|---|
| 1 | `RP-T01` | นักกายภาพบำบัด คนที่หนึ่ง |
| 2 | `RP-T02` | นักกายภาพบำบัด คนที่สอง |

**Service** — ตัวกำหนดความยาวของนัด

| id | code | ชื่อ | นาที |
|---|---|---|---|
| 1 | `PHYSIO_30` | กายภาพบำบัดทั่วไป 30 นาที | 30 |
| 2 | `PHYSIO_60` | กายภาพบำบัดทั่วไป 60 นาที | 60 |
| 3 | `ASSESSMENT` | ประเมินอาการครั้งแรก | 45 |

**Patient** ไม่มี Seed ต้องสร้างเองก่อนทดสอบ

```bash
curl -s -X POST http://localhost:8080/api/patients -H "Content-Type: application/json" -d '{
  "branchCode":"RP","username":"apt-demo","email":"apt-demo@example.com","password":"password123",
  "title":"Mr","fullName":"Somchai Test","birthDate":"1990-01-01","gender":"M","phone":"0812345678",
  "identityDocumentType":"NATIONAL_ID","identityNumber":"1100000000500","address":"Bangkok",
  "drugAllergy":false,"foodAllergy":false,"chronicDisease":"",
  "emergencyContactName":"Somying","emergencyContactRelationship":"Sister",
  "emergencyContactPhone":"0899999999","privacyConsent":true}'
```

จด `id` ที่ได้กลับมาไปใช้เป็น `patientId`

### B.3 ทดสอบด้วย Postman (แนะนำ)

Import ไฟล์

```text
.\SabyeGuyz\docs\api\SabyeGuyz-Appointment.postman_collection.json
```

Collection มี 13 requests พร้อม Test Script ตรวจ Status Code อัตโนมัติ import to postman, กด `Run collection` ได้เลย

**ตัวแปรที่ปรับได้**

| ตัวแปร | ค่าเริ่มต้น | หมายเหตุ |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | |
| `patientId` | `1` | Request `01 Register patient` เขียนทับให้อัตโนมัติ |
| `therapistId` | `1` | |
| `serviceId` | `2` | PHYSIO_60 |
| `slotDate` | `2026-10-05` | เปลี่ยนเมื่อเวลาถูกจองจากการรันรอบก่อน |

### B.4 ทดสอบด้วย curl

Request มาตรฐาน

```bash
curl -i -X POST http://localhost:8080/api/appointments -H "Content-Type: application/json" -d '{
  "branchCode":"RP","patientId":1,"therapistId":1,"serviceId":2,
  "startAt":"2026-10-05T09:00:00+07:00"}'
```

ห้ามส่ง `endAt` และ `status` ระบบคำนวณและกำหนดเองทั้งคู่

### B.5 ชุดเคสทดสอบด้วยมือ

ยิงเรียงจากบนลงล่าง โดยใช้ `therapistId` เดิมและวันเดียวกันทั้งชุด

| # | เปลี่ยนอะไรจาก Request มาตรฐาน | Status | ข้อความที่คาด |
|---|---|---|---|
| 1 | `09:00` (ครั้งแรก) | **201** | `status: CONFIRMED`, มี `Location` header |
| 2 | `09:30` | **409** | `นักกายภาพบำบัดมีนัดหมายในช่วงเวลานี้แล้ว` |
| 3 | `10:00` | **201** | นัดติดกันพอดีจองได้ |
| 4 | `therapistId: 2` เวลา `09:00` | **201** | คนละ Therapist ไม่ชนกัน |
| 5 | ตัด `patientId` ออก | **400** | `fields.patientId: must not be null` |
| 6 | `therapistId: 999999` | **400** | `ไม่พบนักกายภาพบำบัด` |
| 7 | `serviceId: 999999` | **400** | `ไม่พบบริการ` |
| 8 | `branchCode: "ZZ"` | **400** | `ไม่พบสาขาที่เปิดใช้งาน` |
| 9 | `startAt: "2020-01-01T09:00:00+07:00"` | **400** | `ไม่สามารถจองเวลาย้อนหลังได้` |
| 10 | `startAt: "5 ตุลาคม 2026"` | **400** | `รูปแบบข้อมูลที่ส่งมาไม่ถูกต้อง...` |
| 11 | `startAt: "2026-10-05T13:00:00"` (ไม่มี offset) | **400** | `รูปแบบข้อมูลที่ส่งมาไม่ถูกต้อง...` |

เคสที่ 10 และ 11 สำคัญเป็นพิเศษ เพราะถ้าไม่มี Exception Handler ดักไว้จะกลายเป็น **500**
ซึ่งแปลว่าระบบพังจากข้อมูลที่ผู้ใช้กรอก ไม่ใช่ความผิดของ Client

### B.6 ตรวจข้อมูลในฐานข้อมูล

```powershell
docker exec deploy-postgres-1 psql -U clinic -d clinic_erp -c "SELECT id, therapist_id, start_at, end_at, status FROM appointments ORDER BY id;"
```

ตัวอย่างผลที่ถูกต้อง

```text
 id | therapist_id |        start_at        |         end_at         |  status
----+--------------+------------------------+------------------------+-----------
  1 |            1 | 2026-10-05 02:00:00+00 | 2026-10-05 03:00:00+00 | CONFIRMED
  2 |            1 | 2026-10-05 03:00:00+00 | 2026-10-05 04:00:00+00 | CONFIRMED
```

**สิ่งที่ต้องตรวจ 3 อย่าง**

1. **เวลาต้องเป็น UTC** ส่ง `09:00:00+07:00` เข้าไปต้องเก็บเป็น `02:00:00+00`
   ถ้าเห็น `09:00:00+00` แปลว่าเวลาเพี้ยน 7 ชั่วโมง ให้ไปหาว่าที่ไหนใช้ `LocalDateTime` แทน `Instant`
2. **`end_at` ต้องห่างจาก `start_at`** เท่ากับ `default_duration_minutes` ของ Service ที่เลือก
3. **เคสที่ได้ 409 หรือ 400 ต้องไม่มีแถวเพิ่ม** เป็นหลักฐานว่า `@Transactional` rollback ทำงาน

ดูข้อมูล Master ประกอบ

```powershell
docker exec deploy-postgres-1 psql -U clinic -d clinic_erp -c "SELECT id, code, full_name FROM therapists ORDER BY id;" -c "SELECT id, code, name, default_duration_minutes FROM services ORDER BY id;"
```

### B.7 ตรวจเอกสาร API

เปิด `http://localhost:8080/swagger-ui/index.html` ต้องเห็น Tag **Appointments**
พร้อม Response 201, 400 และ 409 ครบ ซึ่งเป็นสิ่งที่ Frontend ใช้อ้างอิง Contract

---

## 4. ปัญหาที่พบบ่อยและวิธีแก้

### ปัญหาตอนรัน Test

| อาการ | สาเหตุ | วิธีแก้ |
|---|---|---|
| `No supported JDK was found` | `run-tests.ps1` หา JDK ที่ path มาตรฐานไม่เจอ | ตั้ง `JAVA_HOME` เองตามหัวข้อ 2 |
| `Schema-validation: missing column` | Entity map ไม่ตรงกับตารางใน Migration | เทียบชื่อ Field กับ `V4__appointment.sql` ทีละบรรทัด |
| `wrong column type ... start_at` | ใช้ `LocalDateTime` แทน `Instant` | เปลี่ยนเป็น `Instant` |
| `LazyInitializationException` | สร้าง DTO นอก `@Transactional` | สร้าง DTO ใน Service เท่านั้น เพราะ `open-in-view=false` |
| `Migration checksum mismatch` | แก้ไฟล์ Migration ที่รันไปแล้ว | ห้ามแก้ของเก่า ให้สร้าง `V5__xxx.sql` ใหม่ |
| `allowsBackToBack...` แดง | เผลอเขียน `<=` หรือ `>=` ใน Query | ต้องเป็น `<` และ `>` |
| `rejectsInactiveService` แดง | ลืม `entityManager.clear()` หลัง Native Query | Native Query ไม่ผ่าน Persistence Context ต้องล้าง Cache เอง |
| `NoSuchElementException` ใน `setUp` | Seed Data ไม่มี | ตรวจว่า Flyway รัน V4 แล้ว และ Code สะกดตรง |

### ปัญหาตอนทดสอบด้วยมือ

| อาการ | สาเหตุ | วิธีแก้ |
|---|---|---|
| ได้ 409 ทั้งที่ควรได้ 201 | เวลานั้นถูกจองจากการทดสอบรอบก่อน | เปลี่ยนวันหรือเวลา หรือล้างข้อมูลตามหัวข้อ 5 |
| ภาษาไทยกลายเป็น `??????` | Terminal ของ Windows แปลง Argument ก่อนส่ง **ไม่ใช่บั๊กของระบบ** | เขียน Body ลงไฟล์แล้วใช้ `curl --data-binary @body.json` หรือใช้ Postman ซึ่งส่ง UTF-8 อยู่แล้ว |
| ได้ 404 ทุก Request | Backend ที่รันอยู่เป็นโค้ดเก่า | ปิดแล้วรัน `spring-boot:run` ใหม่ ตรวจด้วย `/v3/api-docs` |
| `Connection refused` | PostgreSQL ยังไม่ขึ้น | `docker ps` ต้องเห็น `deploy-postgres-1` สถานะ healthy |
| ได้ 500 แทน 400 | ขาด Exception Handler | ตรวจ `ApiExceptionHandler` ว่าดัก `HttpMessageNotReadableException` แล้ว |

---

## 5. ล้างข้อมูลทดสอบ

```powershell
docker compose -f D:\uni_year3\DevOps\ProJect\SabyeGuyz\deploy\docker-compose.yml down -v
docker compose -f D:\uni_year3\DevOps\ProJect\SabyeGuyz\deploy\docker-compose.yml up -d postgres
```

`-v` ลบ Volume ด้วย ทำให้ Flyway สร้าง Schema และ Seed Data ใหม่ทั้งหมด
**Patient ที่สร้างไว้จะหายหมด** ต้องสร้างใหม่ก่อนทดสอบรอบถัดไป

Automated Test ไม่ได้รับผลกระทบ เพราะรันบน H2 In-memory และใช้ `@Transactional` ที่ Rollback ทุกครั้ง

---
