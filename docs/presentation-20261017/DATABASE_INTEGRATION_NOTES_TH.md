# จุดเชื่อม DB ล่าสุดก่อนส่ง develop

ตรวจจาก remote วันที่ 13 ตุลาคม 2026:

- `develop`: `c62d68b07aae805d9b79492bbd6be8c5eee1cdab`
- `feat/database`: `ab4e0d29158560b10857e0a8be5c4bde43c7fcad`, PR [#23](https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/pull/23)
- Candidate DevOps ตอนตรวจ merge preview: `70998160f088776aa38546184c86e9cb4ad7e14a`

## สิ่งที่ยืนยันได้

DB branch ล่าสุดเพิ่ม initial schema ใหม่และ compatibility migration ใน `db/migration`, ย้าย migration เดิมออกจาก runtime, ใช้ `clinic_erp_v2` และ volume `postgres-v2-data` และเพิ่ม optional Manager bootstrap env

Workflow backend ของ DB branch ใช้ PostgreSQL service, Cypress API tests, Java RuntimeCheck และ SchemaCheck ส่วน workflow backend ของงานนี้ใช้ Maven verify และ JaCoCo กับ develop เดิม จึงต้องรักษาทั้ง coverage evidence และ tests ของ DB runtime ใหม่เมื่อแก้ conflict ไม่เลือกทับ workflow ฝั่งใดทั้งไฟล์

CI ของ `feat/database` ที่ SHA ข้างต้นผ่านทั้ง backend/frontend/Compose แล้ว แต่ PR ยังไม่ได้รวมใน develop ณ เวลาตรวจ

## Merge preview แบบไม่แก้ working tree

ตรวจด้วย `git merge-tree --write-tree --name-only HEAD origin/feat/database`, exit 1 พบ conflict 7 paths:

| Path | ประเด็นที่ต้อง review |
|---|---|
| `.github/workflows/backend-ci.yml` | Reusable workflow + JaCoCo ต้องรวม PostgreSQL/Cypress/schema checks ของ DB branch |
| `.github/workflows/frontend-ci.yml` | Runtime/fixture และการเริ่ม backend สำหรับ E2E ต้องตรง schema ใหม่ |
| `backend/pom.xml` | รักษา profiles/plugins/classpath ของ DB branch พร้อม JaCoCo agent/report |
| `frontend/src/features/appointments/data/appointmentPreview.ts` | ฝั่ง develop ลบไฟล์ แต่ DB branch แก้ไข; ต้องตัดสินใจตาม callers ล่าสุด |
| `frontend/src/features/appointments/views/AppointmentView.vue` | UI/runtime integration |
| `frontend/src/features/reports/views/ReportsView.vue` | UI/report API contract |
| `frontend/src/features/settings/views/BackOfficeView.vue` | UI/settings API contract |

เป็นเพียง conflict preview ไม่ได้ merge/resolve/commit DB branch ในงานนี้ การแก้ 4 ไฟล์ UI ต้องทำร่วมกับเจ้าของ module เพื่อไม่ทับงาน Manager ที่รวมเข้า develop วันที่ 10 ต.ค.

## ลำดับรวมที่เสนอให้ทีม

1. ใช้ integration branch จาก develop ล่าสุด รวม DB PR #23 และแก้ UI contracts กับเจ้าของ module
2. นำงาน DevOps เข้ามา โดยรักษา tests และ bootstrap contract ของ DB branch
3. เลือก DB/PVC ใหม่สำหรับ v2 หรือมีแผน migrate data ที่ยืนยัน; อย่านำ V1 ใหม่ไปใช้กับ PVC ของ v1
4. ตรวจ Manager bootstrap/fixtures, patient auth/read/search และ direct SQL readback ให้ตรง table/column จริง
5. ทดสอบ backend unit/integration, PostgreSQL mapping/runtime checks, browser E2E, Compose restart persistence
6. Deploy v2 ใน namespace/DB ใหม่ที่มีชื่อชัด แล้วตรวจ Ingress, Flyway, restore และ PVC proof อีกครั้ง
7. เปิด PR integration เข้า develop และใช้ SHA หลังรวมเป็นชุดส่งวันที่ 17 ต.ค.

Pipeline เดโมรอบนี้ยังผูกกับ runtime develop เดิมและ namespace `clinic-erp-demo-20261017`; template/config/smoke ที่เขียนไม่ใช่หลักฐานว่า v2 ใช้งานกับ Kubernetes ผ่านแล้ว

ถ้า requirement เป็น “เดโม develop ที่รวม DB ล่าสุด” ขั้นตอน integration ข้างต้นเป็นงานที่ยังต้องทำเพิ่ม ไม่ควรใช้ผล DB branch และผล demo branchคนละ SHA มารวมเป็น PASS ชุดเดียว
