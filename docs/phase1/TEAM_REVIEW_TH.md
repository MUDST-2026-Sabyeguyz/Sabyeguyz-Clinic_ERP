# ให้ทีม Review ก่อนขึ้น main

เตรียม backup/draft-phase1 ให้อาจารย์ตรวจ code/E2E/pipeline/tests/coverage วันที่ 16 Oct 2026

## รายการตรวจ

- Clone ตาม README แล้วเปิดแอป/normal login ได้
- Demo data แยกฐานจริง ไม่มี credentials ใน Git
- Unit/API/browser results แยก run พร้อม SHA/fingerprints/exits
- Coverage HTML/XML/LCOV พร้อม denominator/ช่องว่างตามจริง
- Pipeline เรียกคำสั่งเดียวกับ local; remote CI ใช้ผล source นี้และระบุ Billing blocker
- Review diff จาก main โดยรักษา user stories/requirements/feedback ของทีม
- Current-source K8s/business acceptance ที่ยังเปิดต้องระบุไว้ ไม่ใช้ historical proof ปิดแทน

## อนุมัติ

**PENDING — ยังไม่ได้รับคำยืนยันจากเพื่อน และยังไม่ merge/push main**

Review branch/PR พร้อมผล run ให้เพื่อนยืนยัน revision ที่ใช้ส่งอาจารย์ก่อนขึ้น main การสร้าง draft branch/PR ไม่ใช่การอนุมัติ
