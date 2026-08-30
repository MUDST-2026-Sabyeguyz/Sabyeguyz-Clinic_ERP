# วิธีใช้ GitHub Actions โดยไม่ให้เกิดค่าใช้จ่าย

## คำตอบสั้น ๆ

ถ้าต้องการลดความเสี่ยงให้ใกล้เคียงกับ **ไม่เสียเงินแน่นอน** มี 2 แนวทางหลัก:

1. เปิด Repository เป็น **Public** และใช้ Standard GitHub-hosted runner เช่น `ubuntu-latest`
2. ถ้าต้องเป็น Private ให้ใช้ GitHub Free, ตั้ง Budget ของ GitHub Actions เป็น `0 USD`, เปิด `Stop usage when budget limit is reached` และไม่ผูก Payment Method

## ทางเลือกที่ปลอดภัยที่สุด: Public Repository

GitHub ระบุว่า Standard GitHub-hosted runner สำหรับ Public Repository ใช้งานได้ฟรีและไม่จำกัดตามเงื่อนไขของ GitHub

ตัวอย่าง Runner ที่ควรใช้:

```yaml
runs-on: ubuntu-latest
```

ห้ามใช้สิ่งเหล่านี้:

- Larger runner
- Custom runner image
- Cloud Server หรือ Kubernetes Cluster ที่คิดเงิน
- Container Registry ที่เกินโควตา
- Third-party Service ที่คิดเงิน

ข้อเสียคือ Source Code, Issue และ Pull Request จะเปิดเผยต่อบุคคลทั่วไป ดังนั้นห้ามใส่ Password, Token, Private Key หรือข้อมูลผู้ป่วยจริง

## ถ้าต้องใช้ Private Repository

Private Repository มีโควตาฟรี แต่การใช้งานเกินโควตาอาจถูกคิดเงิน จึงควรตั้งค่าดังนี้ก่อนเปิด Workflow ครั้งแรก:

1. ตรวจสอบว่า Organization ใช้ GitHub Free ไม่ใช่ GitHub Team หรือ Enterprise
2. อย่าเพิ่มบัตรเครดิตหรือ Payment Method ถ้าไม่จำเป็น
3. ไปที่ `Organization Settings → Billing and licensing → Budgets and alerts`
4. สร้าง Budget สำหรับ GitHub Actions เป็น `0 USD`
5. เปิด `Stop usage when budget limit is reached`
6. เปิด Email Alert ที่ 90% และ 100% ของ Included Usage
7. ตรวจหน้า Billing เป็นระยะว่าไม่มี Paid Usage

เมื่อโควตาฟรีหมด Workflow ควรหยุดทำงานแทนที่จะปล่อยให้เกิดการใช้งานแบบคิดเงิน

หมายเหตุ: GitHub ระบุว่า Budget ที่เพิ่งสร้างอาจไม่ครอบคลุม Usage ที่เกิดก่อนเวลาสร้าง Budget ดังนั้นควรตั้งค่าก่อนรัน Workflow ครั้งแรก

## Self-hosted Runner

สามารถใช้เครื่องของทีมเองได้:

```yaml
runs-on: self-hosted
```

GitHub ไม่คิดค่า GitHub-hosted runner minutes สำหรับ Self-hosted Runner แต่ยังมีต้นทุนเครื่อง, ไฟฟ้า, Internet และการดูแล Security

ไม่ควรใช้ Self-hosted Runner กับ Public Repository ที่รับ Pull Request จากบุคคลภายนอก เพราะ Workflow อาจรัน Code ที่ไม่ปลอดภัยบนเครื่องของทีม

## ตั้ง Workflow ให้ประหยัด

```yaml
permissions:
  contents: read

concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 10
```

แนวทางเพิ่มเติม:

- ใช้ Linux Standard runner เท่านั้น
- รันเฉพาะ Pull Request และ Branch หลักเท่าที่จำเป็น
- ยกเลิก Run เก่าที่ถูกแทนที่แล้ว
- กำหนด `timeout-minutes`
- ไม่ Upload Artifact ถ้าไม่จำเป็น
- ไม่ Push Docker Image ในระยะแรก
- ไม่ใช้ `macos-*`, `windows-*` หรือ Larger runner
- ไม่สร้าง Cloud Resource จาก Workflow

## สำหรับทีมนี้ควรเลือกแบบใด

ถ้าอาจารย์อนุญาตให้เปิด Source Code:

> ใช้ Public Repository + `ubuntu-latest` + CI อย่างเดียว + ไม่มี Cloud, Registry และ Larger runner

ถ้าต้องเป็น Private:

> ใช้ GitHub Free + Budget `0 USD` + เปิด Stop usage + ไม่ใส่ Payment Method + ตรวจ Billing สม่ำเสมอ

แนวทาง Private อาจทำให้ Workflow หยุดเมื่อใช้โควตาฟรีหมด แต่ปลอดภัยกว่าการเปิดให้ใช้งานต่อจนเกิดค่าใช้จ่าย

## โควตาฟรีที่ควรรู้

GitHub Free สำหรับ Organization มีโควตา Actions โดยสรุป:

| รายการ | โควตา |
|---|---:|
| Actions minutes | 2,000 นาที/เดือน |
| Artifact storage | 500 MB |
| Cache storage ต่อ Repository | 10 GB |

โควตาและเงื่อนไขอาจเปลี่ยนแปลงได้ ให้ตรวจสอบหน้า Billing ของ Organization เสมอ

## แหล่งข้อมูลทางการ

- [GitHub Actions Billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [GitHub Actions Limits](https://docs.github.com/en/actions/reference/limits)
- [Budgets and alerts](https://docs.github.com/en/billing/concepts/budgets-and-alerts)
- [Product usage included with each plan](https://docs.github.com/en/billing/reference/product-usage-included)
- [GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
