# เตรียม Google Calendar สองทาง (งานลำดับรอง)

ผู้ใช้และ feedback ระบุให้เชื่อมสองทาง งานรอบนี้เตรียม design/config contract และ acceptance plan ยังไม่ได้สร้าง OAuth client, เชื่อมบัญชี Google, เพิ่ม endpoint หรือแก้ schema ของทีม

## Flow ที่เสนอให้ backend ทำต่อ

1. ผู้ดูแลที่ได้รับสิทธิ์เชื่อมบัญชีผ่าน OAuth 2.0 web-server flow; ขอ scope เท่าที่ต้องใช้สำหรับอ่าน/เขียน events และเข้าถึง offline ตามนโยบายบัญชี
2. นัดหมายในระบบเปลี่ยน -> บันทึก transaction และ outbox -> worker สร้าง/แก้/ยกเลิก Google event
3. Full sync ครั้งแรก -> เก็บ mapping `appointment_id, calendar_id, event_id, etag` และ `nextSyncToken` หลังอ่านครบทุกหน้า
4. เปลี่ยนใน Google -> notification หรือ periodic poll -> อ่าน incremental changes ด้วย `syncToken` -> validate สาขา/เวลาว่าง/สิทธิ์ -> update นัดในระบบ
5. ใช้ event mapping และ revision/origin เพื่อไม่ส่งการเปลี่ยนที่นำเข้ากลับไปวนซ้ำ; handle retry และ event cancellation อย่าง idempotent

Google push notification แจ้งว่าทรัพยากรเปลี่ยน ต้อง fetch events เพิ่มเพื่อได้รายละเอียด และ channel มีวันหมดอายุ ต้อง renew จึงควรเตรียม fallback polling เมื่อ webhook ใช้งานไม่ได้

หาก incremental sync ตอบ HTTP 410 ต้อง discard sync cursor/cache ของ Calendar แล้ว full sync ใหม่ โดยยังรักษา clinical appointments และความสัมพันธ์ที่ตรวจสอบแล้ว ห้ามลบข้อมูลนัดในระบบจากการ reset sync เพียงอย่างเดียว

แหล่งอ้างอิง: [Google incremental sync](https://developers.google.com/workspace/calendar/api/guides/sync), [Google push notifications](https://developers.google.com/workspace/calendar/api/guides/push), [OAuth web-server flow](https://developers.google.com/identity/protocols/oauth2/web-server)

## Config contract ที่เตรียมไว้

ดู `google-calendar.env.example` ใน folder นี้ เป็นชื่อ config ที่เสนอ ยังไม่มี application code อ่านค่าเหล่านี้ และไม่ใช่ไฟล์ `.env` ที่พร้อม enable ฟังก์ชันได้ทันที

| ค่า | การใช้เมื่อ implement |
|---|---|
| `GOOGLE_CALENDAR_ENABLED=false` | default ปิด ไม่ให้กระทบเดโม CI/CD |
| `GOOGLE_CALENDAR_CLIENT_ID` | OAuth client id ฝั่ง backend |
| `GOOGLE_CALENDAR_CLIENT_SECRET` | provision ผ่าน Secret ของ runtime; ห้ามใส่ frontend/Vite env |
| `GOOGLE_CALENDAR_REDIRECT_URI` | callback URL ที่ลงทะเบียนตรงกับ OAuth client |
| `GOOGLE_CALENDAR_WEBHOOK_URL` | HTTPS callback ที่ Google เข้าถึงได้; localhost demo ใช้ webhook สาธารณะไม่ได้ตรง ๆ |
| `GOOGLE_CALENDAR_TIMEZONE=Asia/Bangkok` | การแปลงวัน/เวลาและ event offset |
| `GOOGLE_CALENDAR_POLL_INTERVAL_SECONDS=300` | ค่าเริ่มต้นที่เสนอสำหรับ polling; ต้องเลือกตาม quota/ความต้องการจริง |

Refresh tokens ต้องเข้ารหัสฝั่ง backend ตามบัญชีที่อนุญาต เก็บ channel token/expiration และ validate callback channel/resource ให้ตรงการเชื่อม ไม่ใส่ tokens ลง logs/artifacts

## เรื่องที่ยังต้องตัดสินใจ

- Calendar กลางคลินิก แยกสาขา หรือแยก PT และใครมีสิทธิ์เชื่อม
- เมื่อสองฝั่งแก้เวลาเดียวกัน ให้ฝั่งใดเป็นหลักหรือรอผู้ดูแลแก้ conflict; ยังไม่ได้เลือกนโยบาย
- Event ที่สร้างจาก Google โดยไม่มี appointment mapping จะรับเข้าระบบหรือไม่ และใช้ข้อมูลผู้ป่วยจากไหน
- เมื่อยกเลิก/ลบใน Google จะ map เป็น Cancelled/Rescheduled หรือ pending approval
- ข้อมูลใดเผยใน Calendar ได้: เสนอใช้ชื่อทั่วไปและ reference แทนข้อมูลสุขภาพ; ให้ทีมยืนยัน payload
- จะส่ง Phase 1 หรือรอบถัดไป; user จัดเป็นงานลำดับรองสำหรับรอบนี้

## Acceptance ที่ควรเตรียม

| กรณี | สิ่งที่ต้องยืนยัน |
|---|---|
| สร้างนัดในระบบ | Google event 1 รายการพร้อม mapping |
| เลื่อน/ยกเลิกในระบบ | event เดิมอัปเดต ไม่มี event ซ้ำ |
| เลื่อน/ยกเลิกใน Google | ระบบเปลี่ยนตาม contract และ audit ผู้ทำ/ต้นทาง |
| แก้สองฝั่งพร้อมกัน | มี conflict outcome ที่ทีมยืนยัน ไม่เขียนทับเงียบ ๆ |
| notification ซ้ำ/outbox retry | ไม่สร้างนัดหรือ event ซ้ำ |
| syncToken 410 และหลายหน้า | Full resync ได้ และ cursor commit หลังครบทุกหน้า |
| channel หมดอายุ/Google 429/5xx | renew/retry/backoff; นัดในระบบยังใช้ได้ |
| disconnect/revoke | หยุด sync และแจ้งผู้ดูแล โดยไม่ลบนัดเดิม |

สำหรับ Kubernetes จริง ต้องมี HTTPS Ingress/DNS ที่ Google เรียกถึง; demo localhost ร่วมกับ polling สามารถเป็นขั้นทดสอบเบื้องต้นหลัง implement แต่ยังไม่ยืนยัน webhook integration
