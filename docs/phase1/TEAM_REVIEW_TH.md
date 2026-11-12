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

## ข้อความสำหรับส่งให้เพื่อนตรวจ

เตรียมชุดส่งตรวจวันที่ 16 ต.ค. ไว้ที่ `backup/draft-phase1` แล้ว รบกวนตรวจ PR ไป main, clone/run ตาม README, ผล code/tests/E2E/coverage และ Compose ของ SHA ที่จะใช้ส่ง รวมถึง requirements documents ที่รักษาจาก main หากยืนยันให้ใช้ revision นี้ส่งอาจารย์ กรุณาระบุ SHA และยืนยันให้ merge เข้า main ก่อนดำเนินการ ตอนนี้ยังไม่ได้ merge main และไม่ได้ถือว่า policy/business/K8s acceptance ที่ยังเปิดเสร็จแล้ว

ผู้ยืนยัน: PENDING · Revision: PENDING · วันที่/เวลา: PENDING
