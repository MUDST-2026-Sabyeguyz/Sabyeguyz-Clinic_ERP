# Jenkins CI หลักและการทดสอบ commit ย้อนหลัง

ชุดนี้ใช้ Jenkins ในเครื่องแทนการใช้ GitHub-hosted runner โดยอ่านคำสั่งทดสอบจาก `.github/workflows/*.yml` **ของ SHA ที่เลือก** ไม่ต้องคัดลอกคำสั่ง tests มาเก็บซ้ำสองชุด ไม่ deploy ระบบจริง

## เปิดระบบ

ต้องมี Docker Desktop (Linux containers), Git, Node และ GitHub CLI ที่ลงชื่อเข้าใช้และอ่าน repo ได้ จาก root repository รัน:

```powershell
.\jenkins-backup\Start-CI.ps1
```

เปิด http://localhost:8086/ ใช้ชื่อ `clinic-admin` และอ่านรหัสจาก `jenkins-backup/runtime/admin-password` เฉพาะในเครื่อง ห้ามแชร์รหัสหรือ commit `runtime/`, `source/`, `results/` ส่วน Jenkins backup เดิมใช้พอร์ต 8085

`compose.ci.yaml` ใช้ volumes ของ `clinic-jenkins-ci` แยกจากระบบเดิมและ Jenkins backup ไม่ใช้ Docker daemon ของเครื่องทดสอบแอป งานรันตามลำดับเพื่อไม่ให้พอร์ตและฐานข้อมูล tests ชนกัน

## ตรวจ commit ปัจจุบันและติดตาม commit ใหม่

```powershell
node jenkins-backup/control.cjs start
node jenkins-backup/control.cjs watch
```

- `Clinic-CI` รับ `SOURCE_SHA` แบบ 40 ตัว และ `WORKFLOWS` ว่างเพื่อรันทุก workflow ณ commit นั้น
- `Clinic-Watch` ดึง refs ทุก 5 นาที ครั้งแรกบันทึกสถานะตั้งต้น ครั้งต่อไปคิวทุก commit ที่เพิ่งปรากฏใน branches ของ repo นี้โดยไม่ซ้ำภายในรอบเดียว
- รอบแรกไม่คิวประวัติทั้งหมด ให้ใช้ inventory ด้านล่างสำหรับรายการที่ติด billing
- เก็บรายงาน JSON, JUnit, logs และ artifacts ในแต่ละ Jenkins build พร้อม SHA และ hash ของ workflow/adapter
- ส่ง commit status `jenkins/clinic-ci` สำหรับการตรวจทั้งหมด หรือ `jenkins/actions-replay` สำหรับ workflow ย้อนหลังที่เลือก
- URL ของ Jenkins เข้าถึงได้จากเครื่องนี้ ผู้ร่วมทีมต้องมี Jenkins URL ที่เข้าถึงได้จึงจะเปิดลิงก์ผลตรวจจากเครื่องอื่นได้

Jenkins ต้องเปิดเครื่องและ Docker ไว้ระหว่างตรวจ การหยุดเครื่องทำให้งานตรวจค้าง/หยุดได้

หลังแก้ pipeline สามารถตรวจ syntax และโหลดนิยาม job ใหม่โดยไม่ restart งานที่กำลังรัน:

```powershell
node jenkins-backup/control.cjs lint
node jenkins-backup/control.cjs reload
```

## ตรวจย้อนหลังเฉพาะ billing

```powershell
node jenkins-backup/inventory.cjs --refresh
node jenkins-backup/control.cjs enqueue
node jenkins-backup/control.cjs status
node jenkins-backup/control.cjs report
node jenkins-backup/control.cjs monitor
```

`inventory.cjs` อ่าน Actions ทุกหน้าและตรวจ annotation จริงของแต่ละ failed run รวม runs ของ SHA เดียวกันเป็นหนึ่งรอบ รัน `enqueue` ซ้ำจะข้าม SHA ที่บันทึกว่าคิวไว้แล้ว ถ้า build ล้มและต้องการลองใหม่ ให้เปิด build เดิมใน Jenkins แล้วใช้ Build with Parameters ด้วย SHA และ workflow เดิม

ไฟล์ `results/replay-manifest.json` เก็บสาเหตุและ Actions run IDs ส่วน `results/replay-report.md` เป็นตารางล่าสุดหลังสั่ง `report` และ `results/deletion-candidates.json` เก็บเฉพาะ runs ที่มีหลักฐานทดสอบสำเร็จตรง SHA/workflow ไม่มีคำสั่งลบ Actions อัตโนมัติ

## การย้ายออกจาก Actions

หลังยืนยันว่า Jenkins รับงานและรัน tests ได้จริง ให้ disable workflows เดิมผ่าน GitHub Actions เพื่อหยุดใช้โควตา พร้อมเปิด `Clinic-Watch` ไว้ ไฟล์ workflow ยังใช้เป็นนิยาม tests และสามารถ enable Actions กลับได้ในภายหลัง การ disable workflow ไม่ลบประวัติเดิม

หากมี required status checks ต้องเปลี่ยนเป็น context ของ Jenkins หลังตรวจสิทธิ์และกฎของ branch แล้ว มิฉะนั้น PR อาจยังรอ check เดิม การส่ง Jenkins status ไม่เปลี่ยนผล Actions เก่าที่ล้มเพราะ billing

## ขอบเขตผลทดสอบ

Worker ใช้ Debian, Java 21, Node 24 และ Chromium แทน GitHub Ubuntu/Chrome โดยบันทึกข้อแตกต่างไว้ใน summary คำสั่ง build/test/API/schema/migration มาจาก source commit เดิม รองรับเฉพาะ actions และเงื่อนไขที่ใช้ใน repository นี้ ส่วนที่ไม่รองรับจะรายงานล้มเหลว ไม่ข้ามแล้วแสดงผ่าน

ขั้น publish/deploy เก่าที่กำหนดให้รันเฉพาะ `workflow_dispatch` บน `xan` จะไม่รันในการตรวจ CI แบบ push นี้ ฐาน PostgreSQL และ Compose project ของแต่ละ job ใช้ใหม่และล้างเฉพาะทรัพยากรทดสอบที่ job นั้นสร้าง

`summary.junit.xml` สรุปผลระดับ workflow/job รายละเอียดจำนวน test cases ของชุดแอปอยู่ใน log และ artifacts ของ tests นั้น ไม่ควรตีความจำนวน JUnit summary เป็นจำนวน app tests ทั้งหมด

หากผลล้มเพราะ infrastructure/เครื่องมือ ให้แก้ runner แล้วทดสอบ SHA เดิมใหม่ แยกจากข้อผิดพลาดของโค้ด ห้ามใช้การลบประวัติ Actions เป็นหลักฐานว่าโค้ดผ่าน

## หยุดโดยเก็บประวัติ

```powershell
docker compose -f jenkins-backup/compose.ci.yaml stop
docker compose -f jenkins-backup/compose.ci.yaml start
```

ห้าม `down -v` ถ้ายังต้องการประวัติ Jenkins หรือ cache นี้
