# เปลี่ยน runtime และ CI เป็น database v2 ล่าสุด

วันที่: 13 ตุลาคม 2026 (Asia/Bangkok)

## เปรียบเทียบ branch และแหล่งที่เลือก

| ส่วน | feat/database | feat/backend-v2-integration | เลือกใช้ |
|---|---|---|---|
| SQL v2 | V1 ใน db/migration, commit 7172e9a แก้ล่าสุด 12 ต.ค. 01:06 | V1 ใน db/migration_new เป็น snapshot ที่ปรับล่าสุด 8 ต.ค. | V1 ของ feat/database โดยไม่แก้เนื้อหา |
| Backend v2 | ก่อนรวม integration รอบล่าสุด | d66f29d วันที่ 12 ต.ค. 23:21 มี contracts และ regression เพิ่ม | clinicv2 และ scenario tests จาก d66f29d แล้วปรับ mapping ให้ตรงกับ SQL ที่เลือก |
| Default runtime | legacy entry point แต่ Flyway ชี้ schema ใหม่ จึงเปิดไม่ได้ | legacy runtime ใช้ migration เดิม; v2 ตรวจแยก | default entry point scan เฉพาะ clinicv2, ใช้ db/migration V1 + V2 |

ไม่ได้ merge frontend ทั้ง branch integration เข้า checkout นี้; frontend ปัจจุบันยังอยู่ และตรวจ type/build/browser กับ runtime v2 จริง

## Migration และ runtime

Confirmed Requirement: นายท่านเลือกเปลี่ยน runtime backend/frontend ให้ใช้ database v2 ล่าสุด

- V1__initial_schema.sql คง SQL ล่าสุดของทีมฐานข้อมูลไว้
- V2__integration_runtime_compatibility.sql เป็น migration ต่อท้ายสำหรับ room_id, bonus_visits, gender_detail, ช่องภาษี และ default_bonus_visits ที่ workflow integration ยังใช้ รวมสถานะ attendance/no-show และฟังก์ชัน receipt sequence แบบ atomic ต่อ branch/month
- ปรับ named enum mappings เป็น position_enum, member_role_enum, action_enum และ appointment_status_enum ตาม SQL ล่าสุด; role/payment method ที่ใช้ String cast ตอน write และ lookup enum เป็น text เมื่อจำเป็น
- Service ใช้ default_validity_value/default_validity_unit และ unit_name; วัน/เดือน/ปีคำนวณตาม calendar unit ไม่แปลงเดือนเป็นจำนวนวันตายตัว
- Course และ transfer เก็บ branch; Course เก็บ validity snapshot ตาม schema ล่าสุด
- total_visits ของ Course เป็นจำนวนสิทธิ์รวม bonus ตาม generated entitled_visits ใน V1 ส่วน API sessions เป็นจำนวนที่ซื้อและ bonusSessions แยกต่างหาก รายงานไม่บวก bonus ซ้ำ
- จำนวนคอมมิชชันครั้งสุดท้ายไม่เกิน locked pool หลังปัดเศษ และคอร์ส Cancelled ใช้ต่อไม่ได้
- HN ต้องถูกสร้างก่อน INSERT แรก และอยู่ใน transaction เดียวกับการลงทะเบียน
- Default application/repositories/entities เป็น clinicv2 เท่านั้น; SecurityConfig และ WebConfig ถูก import โดยตรง
- รักษา MANAGER guards เดิมของ user management, registration, service writes และ commission writes; login คืน role ตัวพิมพ์ใหญ่ตาม frontend พร้อม branch เมื่อบัญชีผูก staff
- Legacy migration อยู่ db/migration_old ใช้เฉพาะ Cypress regression profile พร้อม db/vendor/{vendor}; target 20.1 ใช้กับชุด legacy เท่านั้น

## Database และบัญชีแรก

Compose default database เป็น clinic_erp_v2 และ volume เป็น postgres-v2-data แยกจาก volume postgres-data เดิม ไม่ได้ลบหรือแปลงข้อมูลเดิม

สำหรับติดตั้งใหม่ ให้กำหนดใน .env (ใช้ .env.example เป็นรายการตัวแปร):

```dotenv
POSTGRES_DB=clinic_erp_v2
CLINIC_BOOTSTRAP_MANAGER_USERNAME=<ชื่อ Manager ที่ต้องการ>
CLINIC_BOOTSTRAP_MANAGER_PASSWORD=<รหัสผ่านที่เลือกเองอย่างน้อย 12 ตัวอักษร>
```

คง POSTGRES_PASSWORD และ JWT_SECRET ตาม environment ของตนเอง จากนั้นรัน:

```powershell
docker compose up --build --wait -d postgres backend
```

Provisioner สร้าง Manager เฉพาะฐานข้อมูลที่ยังไม่มี app_user และ serialize ด้วย advisory transaction lock ไม่แก้บัญชีหรือรหัสผ่านของฐานข้อมูลที่มีผู้ใช้อยู่แล้ว หลังสร้างบัญชีให้ลบค่าตัวแปร bootstrap ออกจาก .env และ recreate backend ตามขั้นตอนปกติ บัญชีแรกยังไม่มี Staff; ให้สร้างข้อมูลสาขา/พนักงานและผูกบัญชีที่ใช้ขายคอร์สผ่าน back office

ไม่มีการเปิด public registration และไม่มีรหัสผ่าน production ถูกฝังใน migration

## CI และวิธีตรวจซ้ำ

Backend CI: npm test สำหรับ legacy API regression + v2 service scenarios แล้วเปิด PostgreSQL 16 สำหรับ RuntimeCheck และ SchemaCheck ใช้ V1/V2 ของ production จริง

Frontend CI: unit tests, production build, Docker Compose backend v2/PostgreSQL, Chrome Cypress E2E

สำหรับตรวจ PostgreSQL ในเครื่อง ให้สร้างฐานข้อมูลว่างแยกและตั้ง DB_URL/DB_USER/DB_PASSWORD ก่อน:

```powershell
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean test-compile dependency:build-classpath '-Dmdep.outputFile=target/test-classpath.txt' '-Dmdep.includeScope=test'
$testClasspath = 'backend/target/classes;backend/target/test-classes;' + (Get-Content backend/target/test-classpath.txt -Raw).Trim()
java -cp $testClasspath com.sabyeguyz.schemacheck.RuntimeCheck
java -cp $testClasspath com.sabyeguyz.schemacheck.SchemaCheck
```

RuntimeCheck ต้องใช้ฐานข้อมูลทดสอบว่าง เนื่องจากตั้งชื่อ test accounts/fixtures ไว้คงที่ และจะสร้าง first manager สำหรับ test โดย explicit configuration เท่านั้น

## หลักฐานการตรวจ

- PostgreSQL 16: latest V1 สร้าง schema ได้จริง; Flyway V1 + V2 เปิด runtime ได้
- SchemaCheck: 489 mapped columns, 0 mismatches; Hibernate validate ผ่าน 45 entities และ wiring ผ่าน 20 controllers / 30 services
- Real HTTP regression ครอบคลุม bootstrap manager, invalid/valid login, JWT, branch/service/payment method/patient/course template, sale/receipt/payment, course purchase 5+1, closing, ใช้ครบ 6 ครั้ง, cancellation, report bonus balance และ therapist forbidden writes
- Frontend unit tests 3/3 ผ่าน และ type-check/production build ผ่าน
- Frontend Chrome E2E 2/2 ผ่านกับ packaged v2 backend/PostgreSQL จริง
- Docker Compose project ทดสอบแยก: postgres/backend healthy หลัง up --build --wait
- Backend Cypress regression รอบท้าย: 396/396 ผ่าน (17 specs), npm test exit 0
- Final PostgreSQL acceptance: RUNTIME_CHECK_OK และ SCHEMA_CHECK_OK, exit 0; รวม payment_label persisted และ course-balance report ที่ไม่นับ bonus ซ้ำ
- git diff --check ผ่าน; source V1 ไม่เปลี่ยนจาก 7172e9a

## ขอบเขตและจุดที่ยังไม่ยืนยัน

- ยังไม่ได้ push หรือ rerun GitHub Actions สำหรับ changes ชุดนี้ ผลข้างต้นเป็นการรันในเครื่อง
- ไม่ได้ย้าย business data จาก legacy เข้า v2; volume เดิมถูกเก็บไว้
- ไม่ได้ทดสอบ HTTP ทุก endpoint เช่น course transfer/reversal ทุกกรณี การผ่าน Hibernate/wiring ไม่ใช่หลักฐานว่า business flow ทั้งหมดผ่าน
- Assumption: payment_label ใช้ Deposit เมื่อยังไม่รับเงิน, Installment เมื่อรับบางส่วน และ FullyPaid เมื่อรับครบ; reason_code เริ่ม Other สำหรับ client เดิมที่ส่งเพียงข้อความเหตุผล ควรยืนยันความหมายกับทีมธุรกิจ
- ข้อจำกัดจาก integration เดิม: HN generator ยังเป็น read-then-save; ไม่ได้ยืนยัน concurrent registration บน v2 และควรเพิ่ม atomic allocation/row locking ก่อนใช้งานหลายผู้ใช้จริง การผ่าน legacy concurrency tests ไม่ได้พิสูจน์ข้อนี้
- Open Question: คอมเมนต์ HN ใน V1 ระบุรูปแบบใหม่ YYBBMMNNN แต่คอลัมน์ VARCHAR(10) และ generator integration ใช้ YYBBMMNNNN; รอบนี้คง generator ที่มีอยู่ โดยไม่มีการเดา/เปลี่ยนรูปแบบเลข HN
