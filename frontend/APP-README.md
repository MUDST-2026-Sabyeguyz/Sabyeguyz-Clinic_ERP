# SaByeGuyz Frontend

ระบบหน้าบ้านสำหรับคลินิกกายภาพบำบัด พัฒนาด้วย Vue 3, TypeScript และ Vite

## โครงสร้าง

```text
src/
├─ api/                 API clients
├─ assets/              รูปภาพและฟอนต์
├─ features/            ฟีเจอร์ แยกเป็น components, data, styles และ views
├─ router/              Route และสิทธิ์การเข้าถึง
└─ styles/              Global styles และ role themes
```

## คำสั่งหลัก

```sh
npm install
npm run dev
npm run lint
npm run test:unit -- --run
npm run type-check
npm run build
```

กำหนด Backend URL ผ่าน `VITE_API_BASE_URL` โดยค่าเริ่มต้นคือ `http://localhost:8080`
