# P8/P9 execution report — 15 October 2026 (Asia/Bangkok)

## Outcome and timing

P8 audit/current regression และ P9 disposable migration rehearsal/CI tooling ทำแล้ว. **P8 feature completion และ P9 final release acceptance ยังไม่ครบ** เพราะ Q11/Q12 และ P6/P7 schema/business gates ยังเปิด. ไม่มี merge กลับ `feat/backend-v2-integration`, ไม่มี push/deploy/production migration.

รอบที่ได้รับคำสั่ง wakeup มี timestamp 06:47:33 เวลาไทย และเริ่มตรวจประมาณ 06:48–06:50. ไม่พบ implementation ก่อนรอบนี้บน P8/P9 (HEAD ยังเป็น `b6473b8`) จึงไม่อ้างว่าได้เริ่ม 02:10 หรือทำงานต่อเนื่องตั้งแต่เวลาที่ตั้งไว้. รอบ heartbeat ก่อนหน้าถูกขัดจังหวะตาม context; ไม่มีหลักฐานเพียงพอจะระบุสาเหตุที่เริ่มช้า.

## Branch / tested revisions

- เริ่มบน `feat/p8-p9-reports-release` ที่ `b6473b807c344891e1cc188473194845e164a1a2`, working tree สะอาด.
- `git fetch origin` สำเร็จ. รับ parent `844e83a10c6995fc3752cde81a582c70db18ecef` แบบ fast-forward เพื่ออ่าน handoff ที่ไม่มีใน branch เริ่มต้น; เป็นการนำ parent เข้า branch งาน ไม่ใช่ merge งานกลับ parent.
- P9 tooling commit: `e316797c65b210e1d59b05c3257f65327296efb0` (`test: rehearse legacy migration and reconcile persisted data`).
- Main Java/Vue application source ไม่เปลี่ยนจาก `844e83a`; full API/browser/unit/build/runtime เป็นผลรัน source นี้พร้อม P9 working tree. Rehearsal เป็นผลของ scripts ที่ตรงกับ `e316797`. Documentation/evidence commit ตามมาหลังรายงานนี้และไม่เปลี่ยน code ที่ทดสอบ.
- Parent ยัง `844e83a`. ไม่ fast-forward/local update branch เพื่อน.

## สิ่งที่ทำ

1. อ่าน non-commission spec/plan, handoff, API/source ที่เกี่ยวข้อง และเอกสารผลเพื่อน; แก้ handoff ให้ owner ล่าสุดคือเพื่อน P6/P7, นายท่าน/Alice P8/P9 และชื่อ branch `feat/`.
2. P8 audit: สรุป actual dashboard metrics/payment basis, report authorization gap, Calendar outbound/inbound/access/retry/privacy/duration และ backlog ที่ยังรอคำตอบใน `2026-10-15-P8-Audit.md`.
3. เพิ่ม `rehearse.cjs`: สร้าง databases ชื่อใหม่, สร้าง legacy fixture snapshot หนึ่งชุด, restore ลง target ใหม่สองฐาน, apply active migrations ทั้งหมดเรียง numeric order, import, reconcile, compare manifests. ไม่ drop/overwrite existing databases.
4. เพิ่ม `reconcile.sql`: counts, receipt net ตาม **existing line-total mapping**, original header differences, payments/refunds, course expiry/void/uniqueness, course/entitlement ledger และ FK ทุกตาราง public.
5. ตรวจ fault injection ของ expiry, net amount และ FK orphan บนทั้งสอง target: ต้อง fail และ rollback แล้ว manifest เหมือนเดิม; importer rerun บน populated target ต้องปฏิเสธและข้อมูลไม่เปลี่ยน.
6. เพิ่ม unit tests 3 ข้อสำหรับ future migrations/numeric ordering, duplicate Flyway versions และ manifest expiry differences.
7. CI เพิ่ม rehearsal/หลักฐาน artifact/reconciliation ก่อน HTTP smoke และใช้ migration list/version ปัจจุบันแทน hardcode V1/V2/V3/V1001 กับ baseline=3.
8. แก้ migration guide ให้ receipt format/active-v2-vs-legacy/financial differences ตรงกับ code และระบุ technical rehearsal กับ release readiness ให้ชัด.

## Environment and test safety

- Windows, Java Corretto 21.0.12 (pom release 17), Node 24.18.0, PostgreSQL 16.15.
- Docker ไม่รันเมื่อเริ่ม; เปิด Docker Desktop แล้วสร้าง container ใหม่ `clinic-p8p9-20261015`, PostgreSQL host port 55439 bind เฉพาะ 127.0.0.1.
- API/browser DB `clinic_p8p9`; Runtime/Schema DB `p9_runtime_20261015`; rehearsal source/targets `p9_1792022187505_33540_source`, `_a`, `_b`.
- ไม่มี production data/snapshot ถูกใช้. Snapshot คือ synthetic legacy seeds + `legacy-fixture.sql`. Passwords/tokens ไม่อยู่ในรายงาน/commits.

## ผลรันจริง

| Check | Command / setup | Result |
|---|---|---|
| Backend package | backend: `mvn --batch-mode --no-transfer-progress clean package` | exit 0; build success |
| Full v2 API | backend: `npm run test:v2`, isolated JDBC DB | **217/217**, 24 specs, failed/pending/skipped 0, exit 0 |
| Commission regression | commission + commission-override ใน full API | **17/17** (12+5) |
| Frontend unit | frontend: `npm run test:unit -- --run` | **51/51**, 10 files, exit 0 เมื่อรันนอก sandbox |
| Frontend build | frontend: `npm run build` | vue-tsc + Vite build ผ่าน, exit 0 |
| Full browser | backend `mvn -Pcypress-v2 spring-boot:start`; Vite localhost:5173; frontend `npx cypress run --config-file cypress.v2.config.ts` | **117/117**, 14 specs, failed/pending/skipped 0, exit 0 |
| Production RuntimeCheck | empty DB, env DB_URL/DB_USER/DB_PASSWORD, `java -cp ... com.sabyeguyz.schemacheck.RuntimeCheck` | **RUNTIME_CHECK_OK**, exit 0; uses v2, Flyway V1/V2/V3/V1001 |
| SchemaCheck | same runtime DB, `java -cp ... com.sabyeguyz.schemacheck.SchemaCheck` | **494 mapped columns / 0 problems**, 45 entities, 22 controllers, 31 services; **SCHEMA_CHECK_OK**, exit 0 |
| Rehearsal unit | backend: `node --test scripts/legacy-migration/ci/rehearse.test.cjs` | **3/3**, exit 0 |
| Migration twice | backend: `node scripts/legacy-migration/ci/rehearse.cjs --container clinic-p8p9-20261015` | **MIGRATION_REHEARSAL_OK**, exit 0; stable manifests identical |
| Corruption checks | expiry/receipt total/FK orphan in transaction on each target | **6/6 detected and rolled back** |
| Populated target guard | rerun existing importer after migration on each target | both rejected; manifests unchanged |
| Migrated production app | packaged jar + PropertiesLauncher, port 18081, profile v2, baseline 1001 | started; authenticated existing manager; old patient/course/Assessment receipt/report/dashboard readable |
| Migrated new records | local HTTP smoke + SQL join readback | registered patient id164; old HN retained; new HN `2601100161`; course+payment 10,000 under receipt `RE26100016`, shared **receipt_id=47** |
| Workflow / syntax | YAML parse, embedded migration JS compile, `node --check`, `git diff --check` | pass |

Warnings/failures ที่พบและจัดการ:

- Vitest sandbox แรก fail EPERM rename temp files ก่อน collect tests; rerun นอก sandbox ผ่าน 51/51. ไม่ถือ EPERM เป็น product defect.
- Cypress เตือนว่าลบ screenshots เก่าไม่สำเร็จและ Electron deprecated; ผล suite สุดท้าย exit 0. Vite native config warning ยังมี; ไม่แก้ config ที่ไม่เกี่ยวข้อง.
- RuntimeCheck จงใจตรวจ duplicate PromptPay ทำให้มี expected unique-constraint log แต่ assertion suite จบ RUNTIME_CHECK_OK.
- Reconciliation รุ่นแรก compare header totals ตรง ๆ แล้ว fail 45,800 vs 47,300. ตรวจ SQL พบ existing importer เลือก line totals และ report ต้องเก็บ original differences แยก; ไม่เปลี่ยน importer หรืออ้างว่า header ตรงกัน.
- Local HTTP smoke รอบแรก request ขาด salesperson (400), รอบต่อมาใช้ list-course endpoint ผิด (405). ตรวจ actual contract แล้วแก้ harness, readback รอบสุดท้ายผ่าน. เป็นข้อผิดพลาดของ local test harness; การลงทะเบียน/ซื้อที่สำเร็จก่อนแก้ยังอยู่ใน disposable DB. ผลรอบสุดท้าย `patientCreatedThisRun=false` เพราะ reuse patient164; ไม่อ้างว่าคำสั่งสุดท้ายสร้างผู้ป่วยใหม่อีกครั้ง.

## Migration reconciliation

| Metric | Legacy / expected mapping | Target / result |
|---|---:|---:|
| Patients | 163 | 163 ก่อน HTTP smoke |
| Appointments | 60 | 60 |
| Courses | 3 | 3 |
| Course ledger balance | 10 | 10 |
| Payments | 45,800.00 | 45,800.00 |
| Refunds (absolute legacy amount) | 700.00 | 700.00 |
| Receipt net (existing line-total mapping) | 47,300.00 | 47,300.00 |
| Original receipt header totals | 45,800.00 | **ต่าง 1,500.00**, 15 ใบ ต้อง review |
| Expiry / void / remaining mismatches | 0 | 0 |
| HN / branch-receipt duplicates | 0 | 0 |
| FK orphans | 0 | 0 |

รายใบและ summary: `artifacts/2026-10-15-p9-reconciliation.json`. Local HTTP result: `artifacts/2026-10-15-p9-api-smoke.json`. Technical rehearsal ตั้ง **releaseReady=false**; ปริมาณเงิน header ไม่ถูกทำให้ดูตรงกับ line totals โดยเงียบ.

## Source-to-task / remaining gates

| Requirement / task | ผลรอบนี้ | ยังขาด |
|---|---|---|
| R43 / T21 / P8 Dashboard | audited current payment-based metrics, API/browser regression passed | Q11 customer definitions/metrics/actions และ implementation ใหม่ |
| R23 / T23 / P8 Calendar | audited existing implementation; full API includes provider-fixture tests | Q12 business/privacy approval + permitted live Google validation |
| T25 / Rooms | scope/gate documented | Q12 room busy/repair semantics |
| T26 / Email/reset | scope/gate documented | Q12 delivery/verification/reset policy |
| T28 / P9 migration | repeatable fixture migration, reconciliation, fault injection, migrated HTTP/DB readback | approved P6/P7 schema, Q10 fields, financial header policy, permitted real snapshot + operational backup/rollback rehearsal |
| T29 / CI diagnosis | workflow schema/rehearsal improved and syntax checked | ไม่มี fresh CI failure logs ที่ยืนยัน download failure จึงไม่เพิ่ม retry/cache workaround; GitHub Actions ใหม่ยังไม่รันเพราะไม่ push |
| T30 / legacy cleanup | runtime isolated v2 verified; legacy code/data retained | reference/deployment/rollback review ก่อนเสนอ delete ใน scope อื่น |
| T31 / runtime/E2E | v2 runtime/schema/browser confirmed | final combined-release rerun เมื่อ P6/P7 และ P8 contract พร้อม |

## P6/P7 status and merge decision

Remote branch `origin/feat/p6-p7-identifiers-courses` ใหม่ที่ **edfc4e6** มี implementation **03fa97f** และ progress/decisions docs.

- เพื่อนรายงาน A1 เสร็จ: add therapistName จาก persisted treating PT และตัดสิทธิ์ผู้รับโอนจาก patient ของ checked-in appointment. รายงาน tests ของเพื่อน 223 API/117 browser เป็นหลักฐานของเพื่อน ไม่ใช่ผลที่ Alice รันบน branch นั้น.
- A2/P6 Q3/Q4 และ A3/P7 Q7/Q13 ยังรอคำตอบ. ใบตัดคอร์สพิมพ์และ transferred-member walk-in ยังมี gaps ตามรายงานเพื่อน.
- จึง **ไม่ merge branch เพื่อน** ตามคำสั่งให้ merge เมื่อเสร็จพร้อม evidence/checks; มี commits ใหม่แต่ไม่เท่ากับ P6/P7 complete.
- P8/P9 ของเรายังติด gates จึง **ไม่ merge กลับ parent** เช่นกัน. Focused commit ของ verified tooling เก็บบน branch งานให้ review ได้.

## Review, residual risks, and next action

Self-review เทียบ plan/ownership/actual contracts แล้ว; ไม่ได้อ้าง independent code review หรือ CI hosted pass. ไม่มี applied migration/application/commission code เปลี่ยน. CI paths เรียงไฟล์ใน active migration folder; extra locations ต้อง review หากเปิดใช้นอก default profile.

สิ่งที่ต้องตอบ/ทำต่อ: Q11 และ Q12; รับ approved Q3/Q4/Q7/Q10/Q13 และ final schema จากเพื่อน; ตัดสิน header-vs-lines policy; implement gated features; rerun combined final regression และ permitted-data/operational acceptance ก่อน merge. ได้ส่งคำถาม Q11/Calendar/accounting ในแชตแล้วและยังไม่ถือว่าเงียบคืออนุมัติ.

Local test servers ของรอบนี้หยุดหลังเก็บผล; container/databases คงไว้ตรวจได้ ไม่ลบข้อมูลใด ๆ อัตโนมัติ. เอกสารและ artifacts ที่ commit เก็บผลสำคัญไว้แม้ backend/target ถูก clean ภายหลัง.
