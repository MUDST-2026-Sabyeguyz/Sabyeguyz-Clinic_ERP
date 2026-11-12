# หลักฐาน implementation และ validation

Branch เริ่มจาก develop `dc7e9242cf3d174b93e5dcefccc27d58fe766b64`; รักษางานแก้ frontend tests สองไฟล์เดิมไว้ ไม่มีการเปลี่ยน main/develop โดยตรงหรือเปิด Actions

## ตรวจแล้ว

- Frontend Node 24 รอบก่อน implementation: 153 tests ผ่าน; production build/typecheck ผ่าน (logs `actions-node24-tests.log`, `actions-node24-build.log` ใน clone ไม่ commit)
- Legacy backend Cypress API รอบก่อน: 434 tests ผ่าน; test server หยุดแล้ว (`actions-backend-api-test.log`)
- หลังเพิ่มพอร์ตแยกและ migration entrypoint: rerun พอร์ต 18083 **17 specs / 434 tests ผ่าน** ไม่มี fail/pending/skip และหยุด server สำเร็จ (`actions-isolated-api-test.log`) ขณะทดสอบไม่หยุด frontend ที่พอร์ต 18080 เดิม
- หลังเพิ่ม ActionsMigrationMain: `mvn test` compile main/test source ผ่าน แต่ **0 JUnit tests ถูกค้นพบ** จึงไม่นับเป็น backend tests ผ่าน Pipeline ใช้ Cypress API พร้อมตรวจจำนวน XML มากกว่า 0 แทน
- PowerShell parser และ manifest render ผ่าน
- actionlint official binary 1.7.12 ตรวจ checksum และ workflow ผ่าน พร้อม custom runner label config
- Provision สภาพแวดล้อมแยกจริงแล้ว: namespace/PVC/Secret/ServiceAccount/Role/RoleBinding ใน clinic-actions-demo เท่านั้น
- ใช้ restricted runner identity ทำ Kubernetes server-side dry-run ผ่านสำหรับ ConfigMap/Services/Deployments/migration Job ไม่ใช่ deployment จริง
- RBAC: get Secrets = no, get Nodes = no, create RoleBindings = no
- ดาวน์โหลด official runner ตรวจ SHA256 และลงทะเบียน `clinic-actions-demo-windows` label clinic-k8s-deploy สำเร็จ; start listener แล้ว ต้องดู API สถานะ online แยกจากการมี run
- GitHub runner API ยืนยันสถานะ **online, busy=false** หลัง start listener; ไม่ใช่หลักฐาน workflow/deployment สำเร็จ

## เหตุผลที่ปรับ API test port

Legacy launcher/config/concurrency task เคยกำหนด 18080 ตายตัว ซึ่งมี Compose frontend เดิมใช้อยู่ เพิ่ม `CLINIC_API_TEST_PORT` แบบ allowlist เฉพาะ 18080/18083 และใช้ 18083 ใน pipeline; ไม่ย้ายหรือหยุด service เดิม Profile ทดสอบยัง bind loopback และใช้ H2 แยกเท่านั้น ส่วน 19001 ต้องว่างและ launcher ปฏิเสธถ้าไม่ว่าง

## ยังไม่ใช่ผล deployment

ยังไม่มี GitHub Actions run, Trivy image scan, actual image import, Flyway Job, rollout หรือ authenticated deployed smoke ที่ยืนยันจาก GitHub workflow นี้ เพราะ default branch ยังไม่มี entrypoint และ Actions ยังปิดอยู่ Bootstrap PR และ branch งานต้องได้รับการ review ก่อนอนุมัติ merge/bootstrap และเปิดใช้งานจริง ห้ามอ้างว่าไฟล์หรือ dry-run เป็น rollout สำเร็จ

Runtime credentials, runner settings, logs, XML, node_modules, dist, target และ generated manifests ไม่รวมใน commit รายงานนี้จะเพิ่มผล rerun ที่พอร์ตแยกหลังเสร็จเท่านั้น
