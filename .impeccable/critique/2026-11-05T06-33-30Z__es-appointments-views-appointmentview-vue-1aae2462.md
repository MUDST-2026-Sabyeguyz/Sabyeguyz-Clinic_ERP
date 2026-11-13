---
target: pop-up เลื่อนนัดหมาย
total_score: 24
max_score: 40
na_heuristics:
p0_count: 0
p1_count: 4
target_identity: "file:C:\\D-Derm\\3_y3\\Sabyeguyz-Develop\\frontend\\src\\features\\appointments\\views\\AppointmentView.vue"
target_fingerprint: "sha256:0eab142d238671d101c64e52532be8a924bfeb11f9e083ab4c4c40ef3380758f"
target_path: "C:\\D-Derm\\3_y3\\Sabyeguyz-Develop\\frontend\\src\\features\\appointments\\views\\AppointmentView.vue"
timestamp: 2026-11-05T06-33-30Z
slug: es-appointments-views-appointmentview-vue-1aae2462
---
# ตรวจ UX: Pop-up เลื่อนนัดหมาย

## Design Health Score

| # | Heuristic | Score | เหตุผลย่อ |
|---|---|---:|---|
| 1 | System Status | 3/4 | มี loading, disabled และ success state แต่ flow ยืนยันตอนแก้ไขไม่ถูกเรียกใช้ |
| 2 | Match with Real World | 3/4 | ภาษาโดยรวมชัด แต่ `today -` ไม่ใช่ชื่อบุคลากรที่ผู้ใช้เข้าใจได้ |
| 3 | User Control | 2/4 | มีปุ่มกลับและ Escape แต่ footer ไม่มี Cancel |
| 4 | Consistency | 2/4 | ช่องอ่านอย่างเดียวกับช่องแก้ไขดูคล้ายกัน และชื่อ action กว้างเกินไป |
| 5 | Error Prevention | 2/4 | ตรวจเวลาว่างซ้ำ แต่การแก้ไขข้าม confirmation ที่เตรียมไว้ |
| 6 | Recognition over Recall | 2/4 | ต้องจำค่าจากนัดเดิมเพื่อเทียบกับค่าใหม่ |
| 7 | Flexibility & Efficiency | 2/4 | flow ใช้ได้ แต่ nested time drawer และ action ไม่เฉพาะเจาะจงเพิ่มขั้นตอน |
| 8 | Aesthetic & Minimalism | 3/4 | สะอาดและอ่านง่าย แต่ grouping ยังแบนและช่องหมายเหตุกินน้ำหนักมาก |
| 9 | Error Recovery | 3/4 | รักษาค่าฟอร์มและมีข้อความผิดพลาด แต่ required semantics ยังไม่ครบ |
| 10 | Help & Documentation | 2/4 | มีบริบทนัดเดิม แต่ยังไม่ช่วยเทียบก่อน-หลังอย่างชัดเจน |
| **Total** |  | **24/40** | **Acceptable** |

## Design Specificity Verdict

หน้าตาดูเป็นระบบคลินิกเดียวกับส่วนอื่นและมี palette ที่สงบ แต่โครงสร้างยังเป็นฟอร์ม CRUD ทั่วไปมากกว่าจะเป็น workflow “เลื่อนนัด” ที่ออกแบบเฉพาะ ภาษาปุ่มและการจัดกลุ่มยังไม่ทำให้ผู้ใช้เห็นการเปลี่ยนจากนัดเดิมไปนัดใหม่ในทันที

Automated detector พบ 0 rule violations ใน Vue/CSS ที่ตรวจ จึงไม่มี false positive แต่การตรวจเชิงกลไม่พบปัญหา flow ที่โค้ดข้าม confirmation dialog ซึ่งเป็นความเสี่ยงสำคัญที่สุด

## Priority Issues

- **P1 — Confirmation ตอนแก้ไขไม่ถูกใช้งานจริง:** `requestSaveConfirmation()` เรียก `save()` ทันทีเมื่อเป็น edit ทั้งที่มี dialog “ยืนยันการเลื่อนนัดหมายหรือไม่?” อยู่แล้ว ทำให้คลิกบันทึกครั้งเดียวแล้วเปลี่ยนนัดทันที
- **P1 — ค่า `today -` ในช่องนักกายภาพ:** ดูเหมือนข้อมูลภายในหรือข้อมูลเสีย ลดความน่าเชื่อถือของการเลือกบุคลากร ต้องแสดงชื่อจริง, “ยังไม่ได้เลือก” หรือไม่เปิดฟอร์มจนโหลดสำเร็จ
- **P1 — เปรียบเทียบนัดเดิมกับนัดใหม่ยาก:** นัดเดิมอยู่เป็นประโยคด้านบน แต่นัดใหม่กระจายหลายช่อง ควรมี summary `เดิม → ใหม่` ก่อนยืนยัน รวมวัน เวลา นักกายภาพ และบริการที่เปลี่ยน
- **P1 — Primary action ไม่บอกผลลัพธ์:** “บันทึก” กว้างเกินไป ควรใช้ “ตรวจสอบและเลื่อนนัด” หรือ “ยืนยันการเลื่อนนัด” ตาม flow จริง
- **P2 — ไม่มี Cancel ใน footer:** เพิ่ม “ยกเลิก” ข้าง primary action เพื่อให้ทางออกอยู่ใกล้จุดตัดสินใจ โดยยังคงปุ่มกลับด้านบนได้
- **P2 — Required state ไม่ครบ:** นักกายภาพ วันที่ และเวลาโดน validation แต่ไม่มี marker/ARIA ที่สม่ำเสมอ
- **P2 — Read-only กับ editable มี affordance คล้ายกัน:** ผู้ป่วยควรเป็น summary/card หรือใช้ style locked ที่ชัดเจน ส่วนช่องแก้ไขควรมี surface/focus ที่สม่ำเสมอ
- **P2 — ขอบเขตงานไม่ตรงชื่อ:** “เลื่อนนัดหมาย” แต่แก้ประเภทบริการและนักกายภาพได้ ถ้าต้องการแก้ทุกอย่างควรใช้ “แก้ไขนัดหมาย”; ถ้าเน้นเลื่อน ควรล็อกข้อมูลที่ไม่เกี่ยวข้อง
- **P2 — Mobile footer ไม่ sticky:** มือถือจำเป็นต้องเลื่อนจนสุดเพื่อบันทึกหรือยกเลิก ควรตรึง action bar ด้านล่าง

## Cognitive Load

ระดับปานกลาง: ไม่ผ่าน 3 จุด ได้แก่ chunking, working memory และ one-thing-at-a-time ฟอร์มควรแบ่งเป็น “ข้อมูลนัดหมาย” กับ “วันและเวลาใหม่” และแสดงสิ่งที่เปลี่ยนแทนการให้ผู้ใช้จำเอง

## Persona Red Flags

- **Alex (Power User):** nested time drawer และปุ่ม “บันทึก” ที่ไม่บอกผลลัพธ์ทำให้ flow ช้ากว่าที่ควร
- **Sam (Keyboard/Screen Reader):** focus trap และ Escape ดี แต่ required/invalid association ของนักกายภาพ วันที่ เวลา ยังไม่ครบ
- **Casey (Mobile):** layout หนึ่งคอลัมน์ดี แต่ไม่มี footer sticky และไม่มี Cancel ใกล้ปุ่มหลัก ทำให้ใช้งานมือเดียวและกลับจากการถูกขัดจังหวะยาก

## Minor Observations

- ช่องหมายเหตุ optional กว้างและสูงจนแย่งน้ำหนักจากวัน/เวลา
- padding แนวตั้งมาก ทำให้ action อยู่ไกลจากค่าที่เปลี่ยนบนจอ laptop
- summary นัดเดิมควรรวมบริการและนักกายภาพเดิม หากสองค่านี้แก้ไขได้

## Questions to Consider

1. ต้องการให้หน้าจอนี้เปลี่ยนเฉพาะวัน/เวลา หรือแก้บริการและนักกายภาพได้ด้วย?
2. ก่อนบันทึกควรแสดง confirmation พร้อม `เดิม → ใหม่` หรือให้บันทึกทันที?
3. ต้องการแก้เฉพาะ P1 ก่อน หรือ polish ทั้ง P1/P2 ในรอบเดียว?
