# GitHub Actions สำหรับโปรเจกต์ Physiotherapy Clinic ERP

เอกสารนี้จัดทำเพื่อทำความเข้าใจ GitHub Actions และแนวทางนำมาใช้กับโปรเจกต์ **Physiotherapy Clinic ERP** ซึ่งมี 4 Repository:

- `frontend` — Vue 3 + Vite
- `backend` — Spring Boot
- `deploy` — Docker Compose และ Kubernetes
- `docs` — เอกสารโครงการ

ไฟล์นี้เป็นเอกสารศึกษาและตัวอย่างเบื้องต้น ยังไม่ใช่ Workflow ที่เปิดใช้งานจริงใน Repository

## 1. GitHub Actions คืออะไร

GitHub Actions คือระบบ Automation ของ GitHub ที่สามารถรันคำสั่งให้เราโดยอัตโนมัติเมื่อเกิดเหตุการณ์ เช่น:

- มีคน Push Code
- มีคนเปิดหรือแก้ไข Pull Request
- มีการ Merge เข้า `develop` หรือ `main`
- มีการสร้าง Tag หรือ Release
- เรียกทำงานด้วยตนเองจากหน้า GitHub

ตัวอย่างการทำงาน:

```text
สร้าง feature branch
        |
        v
เปิด Pull Request ไป develop
        |
        v
GitHub Actions ติดตั้ง Dependency และ Run Test
        |
        v
Build ผ่านหรือไม่
        |
   +----+----+
   |         |
  ผ่าน      ไม่ผ่าน
   |         |
Merge ได้   แก้ไข Code
```

Workflow ของ GitHub Actions จะเขียนเป็นไฟล์ YAML และเก็บไว้ในโฟลเดอร์:

```text
.github/workflows/<ชื่อไฟล์>.yml
```

## 2. CI และ CD ต่างกันอย่างไร

### CI: Continuous Integration

CI คือการตรวจสอบ Code อย่างสม่ำเสมอว่า Code ของสมาชิกหลายคนยังทำงานร่วมกันได้หรือไม่

ตัวอย่างงานของ CI:

- ติดตั้ง Dependency
- ตรวจรูปแบบ Code
- Compile หรือ Build
- Run Unit Test
- Run Integration Test
- ตรวจ Dockerfile ว่าสร้าง Image ได้หรือไม่

### CD: Continuous Delivery/Deployment

CD คือการนำผลลัพธ์ที่ Build ผ่านไปเตรียมส่งมอบหรือ Deploy ต่อ

ตัวอย่างงานของ CD:

- สร้าง Docker Image
- Push Image ไป Container Registry
- Deploy ไป Minikube หรือ Kubernetes
- ตรวจ Health Check หลัง Deploy

สำหรับทีมนี้ควรเริ่มจาก **CI ก่อน** แล้วค่อยเพิ่ม CD เมื่อมี Kubernetes Environment ที่พร้อมใช้งานจริง

## 3. GitHub Actions ฟรีหรือไม่

คำตอบสั้น ๆ คือ **มีโควตาฟรี แต่ไม่ใช่ใช้งานได้ไม่จำกัดทุกกรณี**

### กรณี Repository เป็น Public

GitHub ระบุว่า Standard GitHub-hosted runners สำหรับ Public Repository ใช้งานได้ฟรีตามเงื่อนไขของ GitHub

### กรณี Repository เป็น Private

ถ้า Organization ใช้ GitHub Free จะมีโควตารายเดือนสำหรับ GitHub-hosted runners โดยข้อมูลจาก GitHub Docs ปัจจุบันระบุโดยสรุปว่า:

| รายการ | GitHub Free สำหรับ Organization |
|---|---:|
| Actions minutes | 2,000 นาที/เดือน |
| Artifact storage | 500 MB |
| Cache storage ต่อ Repository | 10 GB |

โควตาและเงื่อนไขอาจเปลี่ยนแปลงได้ ควรตรวจสอบหน้า Billing ของ Organization อีกครั้ง

ถ้าใช้เกินโควตา อาจมีค่าใช้จ่ายตามแผนและการตั้งค่า Billing ของบัญชี โดย GitHub จะคิดค่าใช้จ่ายสำหรับการใช้งานที่เกินสิทธิ์ที่รวมอยู่ในแผน

### Self-hosted Runner

Self-hosted runner ไม่คิดค่า GitHub-hosted runner minutes แต่ทีมต้องมีเครื่องหรือ Server ของตนเอง และต้องรับผิดชอบเรื่อง:

- ค่าเครื่องและค่าไฟ
- การติดตั้ง Runner
- Security
- การอัปเดตระบบ
- การดูแลความพร้อมใช้งาน

สำหรับโปรเจกต์การเรียน แนะนำให้ใช้ GitHub-hosted runner ก่อน เพราะตั้งค่าง่ายและไม่ต้องดูแล Server เอง

### สิ่งที่อาจมีค่าใช้จ่ายแยกต่างหาก

GitHub Actions ไม่ได้หมายความว่าบริการรอบข้างทั้งหมดฟรีเสมอไป ตัวอย่างเช่น:

- Container Registry หรือ Package storage เมื่อเกินโควตา
- Cloud Server หรือ Kubernetes Cluster
- ค่าใช้จ่ายของ Cloud Database
- ค่าใช้จ่ายของ Self-hosted runner
- การใช้ Third-party Action หรือบริการภายนอกบางประเภท

ใน Phase แรกของทีม เราจะยังไม่ Push Docker Image ไป Registry และยังไม่ Deploy ไป Cloud จึงควบคุมค่าใช้จ่ายได้ง่าย

## 4. แนวทางที่เหมาะกับ 4 Repository

### Frontend

เมื่อเปิด Pull Request หรือ Push ให้ตรวจ:

```text
npm ci
npm run type-check
npm run build
npm run test:unit
```

เนื่องจาก Vue source อยู่ในโฟลเดอร์ `sabyeguyz` ภายใน Repository `frontend` Workflow ต้องกำหนด Working Directory ให้ถูกต้อง

### Backend

เมื่อเปิด Pull Request หรือ Push ให้ตรวจ:

```text
mvn test
mvn package
```

การตรวจนี้จะเริ่มทำได้หลังจากสร้าง Spring Boot project และมี `pom.xml` แล้ว

### Deploy

เมื่อแก้ไข Docker หรือ Kubernetes ให้ตรวจ:

```text
docker compose config
docker build <frontend-image>
docker build <backend-image>
```

ในระยะแรกควรตรวจว่าสร้าง Image ได้เท่านั้น ยังไม่ต้อง Push Image หรือ Deploy จริง

### Docs

ไม่จำเป็นต้องมี Pipeline ซับซ้อน อาจตรวจเพียง:

- Markdown format
- Link ที่เสีย
- ไฟล์สำคัญมีอยู่ครบ

## 5. ตัวอย่าง Workflow ของ Frontend

ตัวอย่างนี้ใช้เพื่อศึกษาเท่านั้น ยังไม่ควรนำไปวางใช้งานโดยไม่ตรวจ Path และ Script ก่อน

```yaml
name: Frontend CI

on:
  pull_request:
    branches: [develop, main]
  push:
    branches: [develop, main]

jobs:
  test-and-build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: sabyeguyz

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: sabyeguyz/package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Type check
        run: npm run type-check

      - name: Unit test
        run: npm run test:unit -- --run

      - name: Build
        run: npm run build
```

จุดสำคัญคือ `npm ci` ใช้ `package-lock.json` เพื่อให้ CI ติดตั้ง Version ที่ตรงกับทีม และ `--run` ทำให้ Vitest ทำงานแล้วจบใน CI ได้

## 6. ตัวอย่าง Workflow ของ Backend

```yaml
name: Backend CI

on:
  pull_request:
    branches: [develop, main]
  push:
    branches: [develop, main]

jobs:
  test-and-build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
          cache: maven

      - name: Run tests and package
        run: mvn --batch-mode verify
```

Version Java ต้องเปลี่ยนให้ตรงกับ Version ที่ทีมเลือกใน Spring Boot project จริง

## 7. ตัวอย่าง Workflow ของ Deploy

```yaml
name: Deploy Files Validation

on:
  pull_request:
    branches: [develop, main]
  push:
    branches: [develop, main]

jobs:
  validate:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Validate Docker Compose
        run: docker compose config --quiet

      - name: Check Kubernetes manifests
        run: |
          test -d k8s
          find k8s -type f -name '*.yml' -o -name '*.yaml'
```

ตัวอย่างนี้ยังไม่ทำ `docker push` และยังไม่ทำ `kubectl apply` เพราะยังไม่มี Registry และ Cluster ที่ทีมยืนยันแล้ว

## 8. ควรให้ Workflow ทำงานเมื่อใด

แนวทางที่แนะนำ:

| เหตุการณ์ | การทำงาน |
|---|---|
| Push ไป `feature/*` | ตรวจเบื้องต้นได้ ถ้าต้องการ Feedback เร็ว |
| เปิด Pull Request ไป `develop` | ต้อง Run CI และใช้เป็นเงื่อนไข Merge |
| Merge เข้า `develop` | Run CI อีกครั้งเพื่อยืนยัน Integration |
| Merge เข้า `main` | Run CI และเตรียม Release ในอนาคต |
| สร้าง Tag | ค่อยเพิ่มการสร้าง Docker Image หรือ Release เมื่อพร้อม |

สำหรับช่วงเริ่มต้น อาจรันเฉพาะ Pull Request ไป `develop` และ `main` เพื่อลดการใช้ Minutes

## 9. Secrets และความปลอดภัย

ห้ามเขียนข้อมูลเหล่านี้ลงใน YAML หรือ Source Code:

- Password
- Database credential
- Cloud credential
- Personal Access Token
- Docker Registry token
- Private key

ถ้าจำเป็นต้องใช้ ให้เก็บใน GitHub Repository หรือ Organization Secrets และเรียกผ่าน `${{ secrets.NAME }}`

สำหรับ CI ระยะเริ่มต้นของทีมควรพยายามไม่ใช้ Secret จริง โดยใช้ Test Database หรือ Environment จำลองแทน

นอกจากนี้ควรจำกัดสิทธิ์ของ `GITHUB_TOKEN` เท่าที่จำเป็น เช่น:

```yaml
permissions:
  contents: read
```

## 10. GitHub Actions ทำงานร่วมกับ Branch Protection อย่างไร

เมื่อ Workflow ทำงานสำเร็จ GitHub จะสร้าง Check เช่น:

```text
Frontend CI / test-and-build
Backend CI / test-and-build
```

จากนั้น Owner สามารถตั้งค่า Branch Protection ให้:

- `main` และ `develop` ต้องผ่าน Pull Request
- ต้องมี Review อย่างน้อย 1 คน
- ต้องผ่าน Required Status Check
- ห้าม Force Push
- ห้ามลบ Branch หลัก

ควรสร้างและให้ Workflow ทำงานสำเร็จอย่างน้อย 1 ครั้งก่อน จึงค่อยเลือกชื่อ Check ไปตั้งเป็น Required Status Check

## 11. แผนการเริ่มใช้งานที่แนะนำ

### Phase 1: CI เท่านั้น

- สร้าง Workflow ของ Frontend
- สร้าง Spring Boot project และ Workflow ของ Backend
- ตรวจ Docker Compose และ Kubernetes manifest เบื้องต้น
- ยังไม่ Push Image
- ยังไม่ Deploy จริง

### Phase 2: Docker Build

- Build Backend Image
- Build Frontend Image
- ใช้ Docker Compose ทดสอบการเชื่อมต่อ
- เพิ่ม Integration Test

### Phase 3: Kubernetes

- รันบน Minikube
- ใช้ ConfigMap และ Secret
- ตรวจ Readiness/Liveness
- เก็บหลักฐาน Runtime เช่น `kubectl get pods` และ `kubectl get services`

### Phase 4: CD ตามความพร้อม

- Push Image ไป Registry
- Deploy ไป Environment ที่กำหนด
- เพิ่ม Approval ก่อน Deploy Production

## 12. สรุปสำหรับทีม

สำหรับโปรเจกต์นี้ GitHub Actions มีประโยชน์ในการตรวจสอบว่า Code ของสมาชิกยัง Build และ Test ผ่านก่อน Merge โดยไม่ต้องให้ทุกคนตรวจด้วยตนเอง

แนวทางที่เหมาะสมที่สุดในตอนนี้คือ:

> ใช้ GitHub-hosted runner และทำ CI สำหรับ Vue, Spring Boot และไฟล์ Deploy ก่อน โดยยังไม่ทำ Auto Deploy และยังไม่ใช้ Cloud หรือ Container Registry จริง

วิธีนี้เหมาะกับการเรียน DevOps เพราะทีมจะได้ฝึก:

- การเขียน Pipeline as Code
- การทำ Build และ Test อัตโนมัติ
- การใช้ Pull Request และ Branch Protection
- การตรวจ Docker และ Kubernetes manifest
- การแยก CI ออกจาก CD

## 13. แหล่งข้อมูลทางการ

- [GitHub Actions Billing และ Usage](https://docs.github.com/en/actions/concepts/billing-and-usage)
- [GitHub Actions Limits](https://docs.github.com/en/actions/reference/limits)
- [Managing GitHub Actions Settings](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository)
- [GitHub Pricing](https://github.com/pricing)

