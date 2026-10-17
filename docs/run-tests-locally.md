# รันชุดทดสอบทั้งหมดบนเครื่องตัวเอง (ไม่ต้องใช้ GitHub Actions)

ใช้เมื่อโควตานาทีของ GitHub Actions หมด หรืออยากตรวจก่อน push สคริปต์เดียวรันสิ่งที่ workflow ตรวจ

```
bash scripts/run-all-tests.sh            # ทุกอย่าง (ต้องมี Docker)
bash scripts/run-all-tests.sh quick      # frontend: ESLint, type check, unit test, build (ไม่ใช้ Docker)
bash scripts/run-all-tests.sh backend    # ทดสอบ backend (Cypress ยิง API จริงบน PostgreSQL)
bash scripts/run-all-tests.sh frontend   # โหมด quick + ทดสอบหน้าเว็บด้วย Cypress
```

- รันใน Git Bash ที่โฟลเดอร์หลักของโปรเจกต์ ต้องมี Docker (เปิด Docker Desktop ไว้) และ Node.js ตาม `frontend/.nvmrc`
- สคริปต์เปิด PostgreSQL ชั่วคราว, backend (พอร์ต 127.0.0.1:18080) และ Vite (พอร์ต 5173) แล้วลบทิ้งเมื่อจบ ถ้าสองพอร์ตนั้นถูกใช้อยู่ สคริปต์จะไม่เริ่มและไม่แตะของเดิม
- ครั้งแรกดาวน์โหลดแพ็กเกจ Maven (ไม่กี่นาที) เก็บไว้ใน Docker volume `sabyeguyz-m2` (เปลี่ยนด้วย `M2_VOLUME=ชื่อ`)
- ท้ายสุดมีตารางสรุป PASS/FAIL และสุดท้ายบอก `ALL TESTS PASSED` ถ้ามีข้อใดล้ม จะแสดงบรรทัดท้ายของ log ของข้อนั้น
- ถ้า backend ล้มด้วย Flyway "more than one migration with version 1" ให้ลบโฟลเดอร์ `backend/target` แล้วรันใหม่
- ไม่ครอบคลุม: Compose build และ Schema CI ซึ่งยังต้องพึ่ง workflow บน GitHub
