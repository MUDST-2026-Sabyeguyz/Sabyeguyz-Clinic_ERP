# Sabyeguyz Clinic ERP frontend

Frontend สำหรับ **Physiotherapy Clinic ERP — Sabyeguyz** อยู่ที่โฟลเดอร์นี้โดยตรง

## Requirements

* Git
* Node.js `22.18+`
* npm

## Setup

```bash
git clone https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git
cd Sabyeguyz-Clinic_ERP/frontend
npm install
npm run dev
```

เปิด:

```text
http://localhost:5173
```

## การพัฒนา

Frontend รับผิดชอบ:

* UI ตาม Figma
* Pages / Components / Routing
* Form และ Client-side Validation
* Loading / Error / Success State
* API Client
* Frontend Tests

หน้าจอ production ใช้ API จาก backend เท่านั้น ไม่มี mock data หรือ localStorage เป็นแหล่งข้อมูล

## เชื่อม Backend

สร้าง `.env.local`:

```env
VITE_API_BASE_URL=http://localhost:8080
```

## Test

```bash
npm run type-check
npm run test:unit -- --run --passWithNoTests
npm run build

# ต้องเปิด backend จริงไว้ที่ localhost:8080
npm run test:integration
```

## Rules

* ยึด Context, User Story/Acceptance Criteria และ Figma
* API ต้องยึด API Contract ที่ตกลงกับ Backend
* Requirement ไม่ชัดเจนให้ถาม BA

## Git

ใช้ `main`, `develop` และ `feature/<feature-name>`

[Branching Guide](https://github.com/MUDST-2026-Sabyeguyz/docs/blob/main/branching.md)

[Context กลาง](https://github.com/MUDST-2026-Sabyeguyz/docs/blob/main/context.md)
