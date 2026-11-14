# แผนเทสต์ E2E ของ Frontend ขั้นต่อไป (พร้อมวิธีทดสอบเอง)

อัปเดต 14 พ.ย. 2026 · branch `test/e2e-main` (แตกจาก `main`) · วิธีรัน: [run-frontend-e2e.md](run-frontend-e2e.md)

## สถานะตอนนี้

| ชุด | ผล | หมายเหตุ |
|---|---|---|
| Patients | ผ่าน 26/26 | จบแล้ว |
| Manager | ผ่าน 26/26 | จบแล้ว (4 สเปค: checkout, navigation, patients, appointments) |
| Login (`e2e/login.cy.js`) | ผ่าน | อีกสเปค (`task2-real-backend`) บังคับให้รันที่ `127.0.0.1` เท่านั้น ไม่นับ |
| Therapist | ผ่าน 91/91 | ครบทุกสเปค (compensation ใช้ fixture `shift-activity` ย้ายข้อมูลไปเดือนก่อนหน้าเพื่อปิดรอบได้) |

เป้าหมาย: Therapist ที่ผ่านต้องเพิ่มโดยไม่ทำให้ชุดที่ผ่านแล้วถอยหลัง และเคสที่พังเพราะบั๊กจริงต้องมีคนรับไปแก้

## แผน 5 ขั้น (ทำตามลำดับ)

### ขั้น 0 เตรียมระบบเทสต์ (ทำทุกครั้งที่เริ่มทำงานใหม่)
1. เปิด Docker Desktop รอ "Engine running"
2. เปิด PowerShell ที่โฟลเดอร์ root ของโปรเจกต์ แล้วรัน

```powershell
.\scripts\run-frontend-e2e.ps1 -Suite manager -KeepRunning
```

   ใช้เป็นทั้งตัวเปิดระบบและเช็กว่าเครื่องพร้อม (ผ่าน 1/1 แปลว่าพร้อม) ระบบจะค้างเปิดไว้ให้ใช้ต่อ
3. ตรวจก่อนรันเทสต์ทุกครั้ง ต้องได้ `200`

```powershell
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:18080/actuator/health
```

### ขั้น 1 ส่งบั๊ก backend ให้ทีม (ปลดบล็อกราว 14 เคส)
**บั๊ก:** ขายบริการรายครั้งโดยไม่มีนัดหมายได้ 409 เพราะ constraint `chk_sales_transaction_required_staff_visit`
(migration `V1002`) บังคับให้รายการ `SingleVisit` มี `visit_id` ขณะที่ `SaleController` ตั้งใจรองรับ "บริการที่ขายโดยไม่มีนัด
เป็นเครดิตล่วงหน้า" ซึ่งไม่มี visit

**ทำซ้ำเอง:** ระบบเทสต์เปิดอยู่ แล้วรันใน PowerShell (ฐานข้อมูลเทสต์เท่านั้น)

```powershell
$base='http://127.0.0.1:18080'
$token=(Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"manager","password":"manager123"}').token
$h=@{Authorization="Bearer $token"}
Invoke-RestMethod "$base/__cypress/reset" -Method Post -Headers $h | Out-Null   # ล้างฐานข้อมูลเทสต์
# สร้างผู้ป่วย 1 คน (ใช้ฟอร์มใน UI หรือ API) แล้วขายบริการรายครั้งโดยไม่ส่ง appointmentId:
$sale='{"patientId":1,"branchCode":"01","paymentLabelCode":"PAID","items":[{"type":"SERVICE","referenceId":1,"quantity":1}],"payments":[{"paymentMethodCode":"PromptPay","amount":800}]}'
Invoke-RestMethod "$base/api/payments" -Method Post -Headers $h -ContentType 'application/json' -Body $sale
```

ผลที่ผิด: HTTP 409 "ข้อมูลซ้ำหรือขัดกับข้อมูลที่มีอยู่แล้ว" · ผลที่ถูก: 201 และสร้างเครดิตบริการล่วงหน้า

**หลังทีมแก้ ให้ตรวจ:** รัน 3 สเปคที่โดน แล้วต้องผ่านเพิ่มราว 14 เคส

```powershell
.\scripts\run-frontend-e2e.ps1 -Suite therapist -Spec cypress/e2e-therapist/services.cy.js -KeepRunning
.\scripts\run-frontend-e2e.ps1 -Suite therapist -Spec cypress/e2e-therapist/compensation.cy.js -KeepRunning
.\scripts\run-frontend-e2e.ps1 -Suite therapist -Spec cypress/e2e-therapist/transactions.cy.js -KeepRunning
```

### ขั้น 2 แก้เทสต์ที่เป็นข้อความ/selector เล็ก ๆ (เร็วสุด ทำก่อน)
| เคส | ที่ต้องแก้ในเทสต์ |
|---|---|
| AUTH-01 | หลังล็อกอินนักกายภาพไป `/staff/appointments` ไม่ใช่ `/staff/patients` |
| AUTH-03 | ล็อกเอาต์ยังเหลือ `authUser` ใน localStorage (เช็กว่าเทสต์หรือแอปผิด) |
| PAT-03 | ข้อความตรวจเลข 13 หลักและอีเมลเปลี่ยน |
| PAT-05 | ปุ่มยืนยันในกล่อง `.patient-confirmation` ไม่ใช่ "ยืนยัน" แบบเดิม |
| PAT-07 | ข้อความหน้าประวัติว่างเป็น "ผู้ป่วยยังไม่มีรายการบริการหรือคอร์ส" |
| PAT-10 | วันที่อนาคตกดไม่ได้แล้ว (`disabled`) เปลี่ยนเป็นตรวจว่ากดไม่ได้ |

ทดสอบ:

```powershell
.\scripts\run-frontend-e2e.ps1 -Suite therapist -Spec cypress/e2e-therapist/patients.cy.js -KeepRunning
.\scripts\run-frontend-e2e.ps1 -Suite therapist -Spec cypress/e2e-therapist/auth.cy.js -KeepRunning
```

เป้า: patients.cy.js ผ่านเพิ่มจาก 8/15 และ auth.cy.js จาก 33/37 (เหลือเฉพาะที่ต้องตัดสินใจกับทีม)

### ขั้น 3 แก้ชุดหน้านัดหมาย (11 เคส) — ใช้เวลาที่สุด
หน้านัดหมายถูกออกแบบใหม่ ตัวเลือก/ตัวกรอง/ปฏิทินที่เทสต์เรียกไม่มีแล้ว ทำทีละเคส:
1. รันสเปคเดียวแบบเปิดหน้าต่างเพื่อดูหน้าจริง

```powershell
.\scripts\run-frontend-e2e.ps1 -Suite therapist -Spec cypress/e2e-therapist/appointments.cy.js -Headed -KeepRunning
```

2. ดูภาพตอนพังใน `frontend\cypress\screenshots\appointments.cy.js\` และหา selector ใหม่ของ: ตัวเลือกนักกายภาพ, ตัวกรองนักกายภาพ,
   ช่วงวันที่, ปุ่มรายสัปดาห์
3. แก้ helper `dropdown()` ใน `appointments.cy.js` ที่เดียวก่อน (หลายเคสเรียกใช้) แล้วรันซ้ำ

### ขั้น 4 แก้ชุดการใช้สิทธิ์คอร์ส (8 เคส)
กฎใหม่ของทีม: ใช้สิทธิ์ต้องมีนัดหมาย (`appointmentId`) ฝั่ง API มี helper `useCourse()` แล้ว เหลือเคสที่ใช้หน้าจอ
(ใช้สิทธิ์/โอน/ยกเลิก/หมดอายุ) ต้องเปลี่ยนให้เริ่มจากนัดหมายแล้วกดเข้าไปใช้สิทธิ์ ทดสอบด้วย

```powershell
.\scripts\run-frontend-e2e.ps1 -Suite therapist -Spec cypress/e2e-therapist/courses.cy.js -Headed -KeepRunning
```

### ขั้น 5 ตรวจรวมและ commit
1. รันครบทั้ง 3 ชุด (ระบบเปิดอยู่แล้ว ใช้ `-KeepRunning` ต่อได้)

```powershell
.\scripts\run-frontend-e2e.ps1 -KeepRunning
```

2. ดูตารางสรุป: Patients ต้องยัง 26/26, Manager 1/1, Therapist ต้องมากกว่า 47
3. เมื่อพอใจ ค่อย commit บน branch `test/e2e-main` (ยังไม่ได้ commit อะไรเลย) แล้วเปิด PR เข้า `main`
4. ปิดระบบเทสต์

```powershell
docker rm -f clinic-e2e-backend clinic-patients-bcc-pg
docker network rm clinic-e2e-net
# ปิด Vite ที่พอร์ต 5173
Get-NetTCPConnection -LocalPort 5173 -State Listen | ForEach-Object { taskkill /PID $_.OwningProcess /T /F }
```

## กฎความปลอดภัย (ท่องไว้)
- ห้ามรันเทสต์เมื่อ `VITE_API_PROXY_TARGET` ชี้ไปที่ backend/ฐานข้อมูลจริงหรือ Neon เพราะ `/__cypress/reset` ล้างข้อมูลทั้งฐานข้อมูล
- รันทีละชุดเท่านั้น ห้ามรัน Cypress สองตัวพร้อมกัน (ใช้ฐานข้อมูลเดียวกัน)
- ก่อนรันถ้า curl ได้ `000` แปลว่า backend ยังไม่ขึ้นหรือ Docker ปิดไป ไม่ใช่เทสต์พัง

## ตารางติดตามความคืบหน้า

| ขั้น | งาน | ผู้รับผิดชอบ | เป้าหมาย | สถานะ |
|---|---|---|---|---|
| 0 | เตรียมระบบเทสต์ | คุณ | manager ผ่าน 1/1 | ✔ |
| 1 | บั๊ก backend ขายบริการไม่มีนัด | ทีม backend | ผ่านเพิ่มราว 14 เคส | รอส่ง |
| 2 | ข้อความ/selector เล็ก ๆ | คุณ/ผม | patients.cy.js, auth.cy.js เพิ่ม | รอทำ |
| 3 | หน้านัดหมาย (11 เคส) | คุณ/ผม | appointments.cy.js ผ่านเพิ่ม | รอทำ |
| 4 | การใช้สิทธิ์คอร์ส (8 เคส) | คุณ/ผม | courses.cy.js ผ่านเพิ่ม | รอทำ |
| 5 | ตรวจรวม + commit + PR | คุณ | ทั้งหมดไม่ถอยหลัง | รอทำ |
