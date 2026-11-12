# รวมงาน Xan เข้า develop — 13 พฤศจิกายน 2026

## ฐานที่ตรวจ

- develop ก่อนรวม: `b622a7aa7a4cf5b2dc9eb72e5a04fb515508c128`
- xan-merge/user-phase2-completion: `0b85ea1ef0dc65818f08b3e2f0b1d4b788195b73`
- มี common ancestor จริง: `933991d7cc7d9218113301dfbfc24e6211b09b34`
- การรวมครั้งแรกมี 254 conflicted paths เนื่องจาก develop มีการย้อน test merge และใช้ application contracts รุ่นเก่าหลายส่วน ไม่ใช่เพียง SHA เปลี่ยนจาก rewrite

## หลักการแก้ conflicts

- รักษา backend ของ Xan เป็นชุดที่สอดคล้องกัน: branch-scoped closing/payment, read grants แยกจาก mutation, permissions, nullable duration/Calendar Busy, ID card, course collection/idempotency, purchaser-only refund และการย้อน usage หลัง Paid ด้วย Pending adjustment
- ตรวจแล้วไม่มี backend path เฉพาะ develop ที่ขาดใน Xan และ migration version ที่มีร่วมกันไม่มีเนื้อหาแตกต่างกัน จึงไม่แก้ migration ที่ถูกใช้แล้ว
- รักษา UI Phase 2 และนำฟอร์ม backfill ค่ามือจาก develop มารวมในหน้าตั้งค่ารุ่นปัจจุบัน พร้อม regression สองข้อ รักษา intent ที่ลบปุ่มส่งกลับที่ยังไม่รองรับและข้อความ Adjustment แบบตายตัวไว้
- ไฟล์ UI/test รุ่นเก่าที่มีเฉพาะ develop เก็บเป็น archive พร้อม manifest ใต้ `develop-frontend-source-20261113/` ไม่เปิดใช้งานคู่กับ contract รุ่นใหม่ ดูรายละเอียดใน `frontend-develop-xan-resolution-20261113.md`
- รักษา Compose profile v2, guarded seed, loopback database binding, Kubernetes/Jenkins tooling และ Actions definitions ของ Xan โดย repository-level Actions ยังคงปิด
- เอา Vitest cache ใต้ root node_modules ออกจาก tracked files และเพิ่ม ignore ห้ามนับ build artifacts เป็น source
- ไม่ deploy และไม่เปลี่ยน Jenkins, ฐานข้อมูล หรือ demo

## ผลตรวจจริง

- Frontend Vitest: **56 files / 314 tests ผ่าน**, 0 failures
- Frontend build/typecheck: **ผ่าน**
- Backend `mvn -DskipTests package`: **ผ่าน** รวม test-source compilation แต่ไม่ได้ execute tests ด้วยคำสั่งนี้
- Focused Maven: **40 tests, 39 ผ่าน, 1 failure, 0 errors/skips** จาก PaidCourseUsageReversalHttpTest, SharedCourseRefundTest, CourseUsagePrintHttpTest, CommissionVersionEditingTest และ BranchReadGrantHttpTest
- Failure ที่ยังมี: `BranchReadGrantHttpTest.explicitGrantAllowsFilteredForeignUsageReadButHidesOtherStaffPayAndPreservesHomeDefault` line 83 ซึ่งเป็น failure เดียวกับ baseline ของ Xan ก่อนรวม ไม่ใช่ผลผ่านและไม่ได้ลด assertion เพื่อซ่อนปัญหา Backend source ในชุด merge ตรงกับ source Xan ที่มี failure นี้อยู่เดิม
- `git diff --cached --check`: ผ่าน
- ไม่ได้รัน full Maven/V2 browser/deployment ใหม่ และไม่อ้างว่าครบ 119 stories / 483 AC

Logs และ machine-readable Vitest result เก็บอยู่นอก source commit (`xan-develop-*.log` และ `xan-develop-vitest.json`) สำหรับตรวจย้อนหลัง
