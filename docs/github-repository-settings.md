# GitHub Repository Settings

Organization: `MUDST-2026-Sabyeguyz`

Repositories:

- `frontend`
- `backend`
- `deploy`
- `docs`

## สิทธิ์สมาชิก

- เจ้าของโครงการ: Organization Owner และ Repository Admin ตามความจำเป็น
- สมาชิกทีม: Repository Role `Write`
- ไม่ให้สมาชิกทั่วไปเป็น `Admin`
- ใช้ Pull Request และ Code Review เป็นช่องทางหลักในการ Merge

## ตั้งค่า Branch Protection ผ่านหน้า GitHub

ทำซ้ำในแต่ละ Repository:

1. เปิด `Settings`
2. เข้า `Branches` หรือ `Rules` / `Rulesets`
3. เพิ่ม Rule สำหรับ `main` และ `develop`
4. เปิด Require a pull request before merging
5. ตั้ง Required approvals เป็น 1
6. เปิด Require status checks to pass
7. เลือก CI Check หลัง Workflow ทำงานสำเร็จครั้งแรก
8. ปิด Force pushes และ Branch deletion
9. บันทึก Rule

## คำสั่งตรวจสอบก่อนตั้งค่า

```powershell
gh auth status
gh repo view MUDST-2026-Sabyeguyz/frontend
gh repo view MUDST-2026-Sabyeguyz/backend
gh repo view MUDST-2026-Sabyeguyz/deploy
gh repo view MUDST-2026-Sabyeguyz/docs
```

คำสั่งนี้เป็นการตรวจสอบเท่านั้น ไม่ได้เปลี่ยนแปลงสิทธิ์หรือ Settings

## Project Board

มี Organization Project Board อยู่แล้ว จึงไม่สร้างหรือปรับแก้จาก Repository นี้ ให้ BA เป็นผู้กำหนด Gantt Chart และสถานะงาน
