# Branching และ Pull Request

## Branch ที่ใช้

```text
main
develop
xan
feature/<feature-name>
bugfix/<issue-name>
hotfix/<issue-name>
```

## สร้าง Branch เริ่มต้น

สำหรับ Repo ที่มี Commit แล้ว:

```powershell
git switch main
git pull --ff-only origin main
git switch -c develop
git push -u origin develop
```

สำหรับ `backend` และ `deploy` ที่ยังไม่มี Commit ให้ทำ Initial Commit บน `main` ก่อน แล้วจึงสร้าง `develop`

## ทำงาน Feature

```powershell
git switch develop
git pull --ff-only origin develop
git switch -c feature/hello-world
```

หลังแก้ไขและตรวจสอบแล้ว:

```powershell
git add .
git commit -m "feat: add hello world baseline"
git push -u origin feature/hello-world
```

จากนั้นเปิด Pull Request ไปยัง `develop`; ใช้ `xan` สำหรับ integration branch ที่ทีมกำหนด

## กติกา Branch Protection ที่แนะนำ

- ห้าม Push ตรงเข้า `main` และ `develop`
- ต้องใช้ Pull Request
- ต้องมี Review อย่างน้อย 1 คน
- ต้องผ่าน CI Status Check
- ห้าม Force Push
- ห้ามลบ Branch หลัก
- ให้ Owner bypass ได้เฉพาะกรณีฉุกเฉิน
