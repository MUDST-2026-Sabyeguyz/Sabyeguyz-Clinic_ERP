# การรวม branch สำรอง DevOps — 16 October 2026

ใช้ `backup/draft-phase1` เป็นชุดส่งตรวจเพียงชุดเดียวแทน `backup/devops-phase-1` โดย PR #51 ยังรอ review ก่อนเข้า `main`

## สิ่งที่เก็บไว้

- รวมประวัติทั้งหมดของ `backup/devops-phase-1` ที่ commit `a3c1338fe82cd31c49a78445540edfecd41163cf` เป็น parent ของ merge commit นี้ จึงค้นไฟล์และ commit เก่าได้หลังลบชื่อ branch
- เก็บ [Kubernetes template เดิมและข้อจำกัด](kubernetes/README.md) ไว้เป็นหลักฐานทางประวัติศาสตร์ โดยไฟล์ตรงกับ source commit เดิม
- คง Jenkins, Docker Compose, สคริปต์ทดสอบ และ workflow ของ draft ที่ใช้กับแอปปัจจุบัน

## ส่วนเก่าที่ไม่นำกลับมารัน

| ส่วนเดิม | เหตุผลที่ใช้ของ draft ต่อ |
| --- | --- |
| `backend-verify.yml` | เรียก profile `database-v2` และ cleanup script ที่ถูกถอดจาก draft; ใช้ `phase1-review.yml` กับตัวตรวจ `clinicv2` ต่อ |
| `frontend-verify.yml` | ใช้พอร์ตและคำสั่ง integration ของแอปรุ่นเก่า; draft มี pipeline ตรวจโค้ด, E2E, coverage และ Compose อยู่แล้ว |
| Jenkins Dockerfile / Jenkinsfile | draft มีการแก้ apt HTTPS และการ fetch bundle ของ develop ที่ใหม่กว่า |
| `scripts/run-all-tests.sh` | draft เพิ่มการตรวจพอร์ต, การเริ่ม Vite และเลือก Cypress spec แล้ว จึงคงฉบับที่แก้ไว้ |
| template Kubernetes | ใช้ profile `database-v2`, migration และ readiness indicator รุ่นเก่า จึงเก็บเป็นเอกสารอ้างอิง ไม่ใช้ deploy `clinicv2` |
| รายงานและคำแนะนำ schema รุ่นเก่า | อ่านย้อนหลังได้จาก source commit; ใช้เอกสาร `docs/phase1/` และ requirements ใน draft สำหรับการส่งตรวจ |

การรวมครั้งนี้คง application source, workflows และ configuration ของ draft ทุกไฟล์ เพิ่มเฉพาะเอกสาร archive และลิงก์นี้ ไม่ได้ยืนยันการ deploy Kubernetes ของ draft หรือผลรันแอปใหม่

## เรียกดู source เดิม

```bash
git show a3c1338fe82cd31c49a78445540edfecd41163cf:README.md
git ls-tree -r --name-only a3c1338fe82cd31c49a78445540edfecd41163cf
```

คู่มือใช้งานปัจจุบัน: [คู่มือผู้ตรวจ Phase 1](../../phase1/REVIEW_GUIDE_TH.md)
