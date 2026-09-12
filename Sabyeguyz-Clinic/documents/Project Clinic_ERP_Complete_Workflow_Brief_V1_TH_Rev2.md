-
CLINICERPWORKFLOWBRIEF V1
CLINIC ERP
-
Complete Workflow Brief V1
สำาํ หรัับโครังงานออกแบบ UX/UI รัะบบบรัิหารัคลิินิก
แนวคดิ ของโจทย์์
เอกสำารัน้ีรัะบุ Business Workflow แลิะขอ้ มููลิท่ีรัะบบต้อ้ งรัองรัับเทา่ นัน้ นักศึกึ ษามูีหน้าท่ีวเิ ครัาะหแ์ ลิะออกแบบ
Information Architecture, User Flow, UX แลิะ UI ด้วย์ต้นเอง โดย์ไมูก่ าํ หนดจาํ นวนหน้า เมูนู ต้าํ แหน่งปุุ่่มู หรัือรัูปุ่แบบ
Dashboard
Version1.0|August2026
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
1. Project Overview
Clinic ERP เปุ่็นรัะบบสำาํ หรัับบรัิหารักรัะบวนการัของคลิินิก ต้ัง้ แต้ก่ ารัต้ัง้ คา่ รัะบบหลิังรั้าน การัลิงทะเบีย์นผูู้ปุ่้ ่วย์ การันัดหมูาย์ การั
เขา้ รัับบรัิการั การัขาย์รัาย์ครััง้ หรัือคอรั์สำ การัใช้สำ้ ทิ ธิ์ิ์คอรั์สำ การัช้าํ รัะเงิน คา่ มูือ/คา่ คอมูมูช้ิ ช้ั่น ไปุ่จนถึึง Dashboard แลิะรัาย์งานย์อ้ น
หลิัง
หลิักการัสำาํ คัญ
Back Office กาํ หนดวา่ “รัะบบมูีอะไรัใหเ้ ลิือกแลิะใช้ง้ าน” สำว่ น Clinic Operation นําคา่ ท่ีต้ัง้ ไวมู้ าสำรั้าง Transaction จรัิง
แลิะ Transaction เหลิา่ นัน้ กลิาย์เปุ่็น Dashboard / Reports
System Domains
 Domain A - Back Office / System Administration
 Domain B - Clinic Operation / Daily Workflow
 Domain C - Dashboard / Reports
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
2. Back Office / System Administration Workflow
รัะบบต้อ้ งมูีพื้้ืนท่ีสำาํ หรัับผูู้ด้ ูแลิรัะบบต้ัง้ คา่ ขอ้ มููลิหลิักกอ่ นท่ี Front Office จะนําไปุ่ใช้ง้ านจรัิง โดย์ขอ้ มููลิเหลิา่ น้ีไมูค่ วรัถึูก Hard-
code ลิงในโปุ่รัแกรัมู
Setting Business Requirement
สำรั้าง/แกไ้ ขสำาขา, Branch Code, ขอ้ มููลิต้ดิ ต้อ่ , Active/Inactive
Branch Setting
แลิะใช้ ้ Branch Code เปุ่็นสำว่ นหนึ่งของ HN
Users / Roles / Permissions สำรั้างบัญช้ีผูู้ใ้ ช้ ้ กาํ หนดบทบาท สำทิ ธิ์ิ์ แลิะสำาขาท่ีสำามูารัถึเขา้ ถึึง
ขอ้ มููลิพื้นักงาน ต้าํ แหน่ง สำาขา แลิะสำถึานะ Active/Inactive
Staff / Physiotherapists เพื้่ือใช้ก้ ับ Appointment, Treatment, Sales, Commission
แลิะ Reports
กาํ หนดรัาย์การัต้รัวจ บรัิการัรัาย์ครััง้ แลิะคอรั์สำ รัวมูรัาคา
Services / Courses
จาํ นวนครััง้ Bonus วันหมูดอาย์ุ แลิะสำถึานะใช้ง้ าน
กาํ หนดช้อ่ งทางช้าํ รัะเงิน เช้น่ Cash, Transfer, QR, Credit
Payment Methods
Card แลิะสำามูารัถึเปุ่ิด/ปุ่ิดได้
กาํ หนดห้องหรัือ Resource ท่ีอาจใช้ก้ ับการันัดหมูาย์ เช้น่
Room / Resource Setting Treatment Room, Exercise Area หรัือ Running Lab; V1
สำามูารัถึถึือเปุ่็น Should Have
กาํ หนดกต้ิกาคา่ มูือหรัือคา่ คอมูมูช้ิ ช้ั่นสำาํ หรัับการัรัักษาแลิะ/หรัือ
Commission Rules
การัขาย์
ขอ้ มููลิท่ี Admin เพื้ิมู่ /แกไ้ ขได้ เช้น่ Customer Group,
Master Data Referral Channel, Insurance Company แลิะรัาย์การั
Dropdown อ่ืน ๆ
UX/UI Challenge
นักศึกึ ษาต้อ้ งเปุ่็นผูู้ต้้ ัดสำนิ ใจเองวา่ Settings ควรัถึูกจัดกลิุมู่ อย์า่ งไรั แลิะจะออกแบบใหผู้้ ูด้ ูแลิรัะบบเขา้ ใจได้งา่ ย์โดย์ไมูท่ าํ ให้
รัะบบดูซัับซั้อนเกินไปุ่
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
3. Patient Registration Workflow
Patient Profile - Required Information
Field รัาย์ลิะเอีย์ด บังคับ
ปุ่รัะเภทลิูกคา้ ไทย์ / ต้า่ งช้าต้ิ ✓
คาํ นําหน้า เช้น่ ด.ช้. ด.ญ. นาย์ นาง นางสำาว ✓
ช้่ือภาษาไทย์ ช้่ือแลิะนามูสำกุลิแย์ก Field ✓
นามูสำกุลิภาษาไทย์ แย์กจากช้่ือ ✓
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
ช้่ือเลิน่ Optional -
เพื้ศึ Required ✓
เลิขบัต้รัปุ่รัะช้าช้น Optional -
หนังสำอื เดินทาง Optional -
วันเกิด Optional -
กรัุปุ่๊ เลิือด Optional -
สำัญช้าต้ิ Optional -
เบอรั์โทรั Required ✓
ท่ีอย์ู่ Optional -
กลิุมู่ ลิูกคา้ Configurable Master Data -
ช้อ่ งทางการัมูา Configurable Master Data -
ปุ่รัะเภทปุ่รัะกัน / บรัิษัทปุ่รัะกัน Configurable Master Data -
HN / Patient ID
System Generated HN
รัูปุ่แบบ: YY + BB + MM + NNNN | ต้ัวอย์า่ ง 26 + 01 + 12 + 0001 = 2601120001
 YY = ปุ่ี ค.ศึ. 2 หลิักทา้ ย์ของวันท่ีลิงทะเบีย์น
 BB = Branch Code 2 หลิัก เช้น่ 01, 02
 MM = เดือนท่ีลิงทะเบีย์น 2 หลิัก
 NNNN = Running Number 4 หลิัก แย์กต้ามูสำาขาแลิะเดือน แลิะ Reset ทุกเดือน
 HN ต้อ้ ง Generate อัต้โนมูัต้ิ ไมูซั่ าํ้ ทัง้ รัะบบ แลิะไมูเ่ปุ่ลิ่ีย์นเมู่ือผูู้ปุ่้ ่วย์ไปุ่ใช้บ้ รัิการัสำาขาอ่ืน
,
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
4. Appointment & Patient Visit Workflow
ขอ้ มููลิขัน้ ต้า่ํ ของการันัดหมูาย์
 Patient
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
 วันท่ีแลิะเวลิา
 สำาขา
 ผูู้ใ้ หบ้ รัิการั / นักกาย์ภาพื้บาํ บัด
 ปุ่รัะเภทบรัิการั
 สำถึานะนัดหมูาย์
สำถึานะท่ีรัะบบต้อ้ งรัองรัับ
 Confirmed / นัดหมูาย์แลิว้
 Arrived / มูาถึึงคลิินิก
 In Service / เขา้ รัับบรัิการั
 Completed / เสำรั็จสำน้ิ
 Cancelled / ย์กเลิิก
 Rescheduled / เลิ่ือนนัด
 ไมูมู่ าต้ามูนัด
NoShow/
หลิักการั
เอกสำารัไมูก่ าํ หนดวา่ การันัดหมูาย์ต้อ้ งเปุ่็น Calendar, List หรัือ Timeline นักศึกึ ษาต้อ้ งเลิือก Representation ท่ีเหมูาะสำมู
กับผูู้ใ้ ช้เ้ อง
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
5. Service, Sales & Course Workflow
บรัิการัหลิักของ แบง่ เปุ่็น 3 กลิุมู่ : ต้รัวจ/ปุ่รัะเมูนิ รัักษารัาย์ครััง้ แลิะรัักษาด้วย์คอรั์สำ
V1 ,
 Assessment / ต้รัวจแลิะปุ่รัะเมูนิ
 Single Visit / โปุ่รัแกรัมูรัักษารัาย์ครััง้
 Course / Package / โปุ่รัแกรัมูรัักษาแบบคอรั์สำ
เปุ่้าหมูาย์ของ Workflow
ผูู้ใ้ ช้ง้ านต้อ้ งสำามูารัถึจบการัขาย์ การัซั้ือคอรั์สำ การัใช้ค้ อรั์สำ แลิะการัช้าํ รัะเงินได้อย์า่ งต้อ่ เน่ือง โดย์ลิดการัสำลิับบรัิบทท่ีไมูจ่ าํ เปุ่็น
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
Business Rules ท่ีต้อ้ งรัองรัับ
 ผูู้ปุ่้ ่วย์สำามูารัถึซั้ือบรัิการัรัาย์ครััง้ ได้
 ผูู้ปุ่้ ่วย์สำามูารัถึซั้ือ Course/Package ได้ โดย์รัะบบรัู้จาํ นวนครััง้ รัาคา แลิะวันหมูดอาย์ุ
Bonus
 ผูู้ปุ่้ ่วย์ท่ีมูี Active Course สำามูารัถึใช้สำ้ ทิ ธิ์ิ์แลิะต้ัดจาํ นวนครััง้ ได้
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
 รัองรัับกรัณีีซั้ือคอรั์สำแลิะใช้ ้ ครััง้ ในวันเดีย์วกัน
1
 รัะบบต้อ้ งทรัาบ Purchased / Used / Remaining
 การัเปุ่ลิ่ีย์นแปุ่ลิง Course Balance ต้อ้ งต้รัวจสำอบย์อ้ นหลิังได้
 หน้ารัาย์การัขาย์ของ เน้น Assessment, Single Treatment แลิะ Course; Lab/ย์า/อุปุ่กรัณี์ไมูอ่ ย์ูใ่ น Daily
V1
Workflow แต้โ่ ครังสำรั้างอนาคต้สำามูารัถึรัองรัับได้
Course Transaction Concept
Course Ledger
+13 Purchase 1 -T reatment 3 -T ransfer Out + 1 Adjustment
รัะบบไมูค่ วรัแกเ้ พื้ีย์งเลิข Remaining โดย์ไมูมู่ ีปุ่รัะวัต้กิ ารัเปุ่ลิ่ีย์นแปุ่ลิง
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
6. Course Transfer, Payment & Cancellation
Course Transfer
 เจา้ ของคอรั์สำเลิือกจาํ นวนครััง้ ท่ีต้อ้ งการัโอน
 เลิือก Patient ผูู้รั้ ับ
 รัะบบต้รัวจสำอบจาํ นวนครััง้ คงเหลิือกอ่ นย์ืนย์ัน
 เมู่ือย์ืนย์ัน รัะบบลิดสำทิ ธิ์ิ์เจา้ ของเดิมูแลิะเพื้ิมู่ สำทิ ธิ์ิ์ผูู้รั้ ับ
 ต้อ้ งต้รัวจสำอบย์อ้ นหลิังได้วา่ ใครัโอนใหใ้ ครั ก่ีครััง้ เมู่ือไรั แลิะผูู้ใ้ ช้ง้ านคนใดเปุ่็นผูู้ท้ าํ รัาย์การั
Payment
 คาํ นวณีย์อดจาก Service / Course
 เลิือกช้อ่ งทางช้าํ รัะเงินจากคา่ ท่ี Admin ต้ัง้ ไว้
 ย์ืนย์ัน Transaction แลิะบันทึกรัาย์ได้
Cancellation / Void
 Transaction ท่ีเกิดข้ึนแลิว้ ไมูค่ วรัถึูก Delete ท้ิง
 ผูู้ใ้ ช้เ้ลิือกย์กเลิิกแลิะรัะบุเหตุ้ผู้ลิ
 รัะบบ Reverse ผู้ลิกรัะทบ เช้น่ รัาย์ได้ หรัือ Course Transfer
,CoursePurchase,CourseUsage
 เกบ็ ปุ่รัะวัต้ผู้ิ ูย์้ กเลิิก เวลิา แลิะเหตุ้ผู้ลิเพื้่ือ Audit ย์อ้ นหลิัง
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
7. Staff, Sales & Commission Workflow
Key Concept
Treating Staff แลิะ Salesperson อาจเปุ่็นคนลิะคนกัน รัะบบจึงต้อ้ งแย์กขอ้ มููลิสำองบทบาทน้ีออกจากกัน
Workflow
 เมู่ือเกิด Treatment Transaction รัะบบรัะบุผูู้รั้ ักษา
 เมู่ือเกิด Sales Transaction รัะบบรัะบุผูู้ข้ าย์
 รัะบบนํา Commission Rule ท่ีต้ัง้ ไวมู้ าใช้ก้ ับ Transaction ท่ีเก่ีย์วขอ้ ง
 บันทึกคา่ มูือ/คา่ คอมูมูช้ิ ช้ั่นเพื้่ือนําไปุ่ใช้ใ้ นรัาย์งานรัาย์บุคคลิแลิะรัาย์สำาขา
Treatment / Sale T reating Staff + Salesperson A pply Commission Rule C ommission
Record R eport
ขอ้ มููลิท่ีควรัต้รัวจสำอบย์อ้ นหลิังได้
 Transaction ต้น้ ทาง
 Patient

Branch
 Treating Staff
 Salesperson
 Commission Rule ท่ีใช้้
 จาํ นวนเงนิ คา่ มูือ/คา่ คอมูมูช้ิ ช้ั่น
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
8. Dashboard & Reports Workflow
Dashboard Information
 จาํ นวนผูู้ปุ่้ ่วย์ (Unique Patients)
 แย์กเพื้ศึช้าย์ / หญงิ
 จาํ นวน
Visits
 รัาย์ได้รัวมู
 รัาย์ได้จาก Course Sales
 รัาย์ได้จาก Single Visit Sales
 สำามูารัถึดูต้ามูช้ว่ งเวลิาแลิะสำาขา
Reports ท่ีต้อ้ งรัองรัับ
Report Purpose
รัาย์ได้รัวมู รัาย์ได้คอรั์สำ รัาย์ได้รัาย์ครััง้ แลิะ Filter ต้ามูช้ว่ ง
Revenue Report
เวลิา/สำาขา
Course Balance Report Course ของผูู้ปุ่้ ่วย์แต้ลิ่ ะรัาย์ ซั้ือ ใช้ ้ โอน แลิะคงเหลิือก่ีครััง้
Course Transfer Report จากเจา้ ของเดิมูไปุ่ย์ังผูู้รั้ ับ พื้รั้อมูจาํ นวนครััง้ แลิะปุ่รัะวัต้ย์ิ อ้ นหลิัง
Staff Sales Report ย์อดขาย์ทัง้ สำาขาแลิะรัาย์บุคคลิ
Commission Report คา่ มูือ/คา่ คอมูมูช้ิ ช้ั่นของผูู้ใ้ หบ้ รัิการั
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
- -
9. End to End Business Workflow
Core Daily Flow
PatientRegistration Appointment Arrival Service Sales /CoursePayment Commission Next AppointmentDashboard /
Reports
Supporting Flows
 Course Purchase
 Course Usage

Buy&UseSameDay
 Course Transfer
 Cancellation / Void

BackOfficeConfiguration
System Thinking ท่ีคาดหวัง
 คา่ ท่ี Admin ต้ัง้ ไวต้้ อ้ งถึูกนําไปุ่ใช้ใ้ น Daily Operation โดย์ไมู ่ Hard-code
 Transaction ท่ีเกิดข้ึนต้อ้ งเช้่ือมูโย์งกับ Patient, Branch, Staff แลิะเวลิา
 ขอ้ มููลิ Dashboard / Reports ต้อ้ งมูาจาก Transaction จรัิง ไมูใ่ ช้ก่ ารักรัอกสำรัุปุ่ซัาํ้
 Workflow ต้อ้ งลิดขัน้ ต้อนซัาํ้ แลิะลิดโอกาสำผู้ิดพื้ลิาดของผูู้ใ้ ช้ง้ านหน้ารั้าน
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
10. Scope Boundaries for V1
In Scope

BackOfficeSettings
 Patient Registration & HN
 Appointment / Visit
 Assessment / Single Treatment / Course
 Course Purchase / Usage / Transfer
 Payment
 Cancellation / Audit History
 Staff / Commission
 Dashboard / Reports
Out of Scope for V1
 Supplier / Procurement Workflow
 Advanced Stock Transfer / Requisition
 Drug Interaction
 Pharmacy Workflow
 Laboratory Integration
 Insurance Claim Integration
 Full Accounting System
 Production Payment Gateway
 Production LINE Integration
หมูาย์เหตุ้
Lab / ย์า / อุปุ่กรัณี์อาจถึูกออกแบบเผู้่ือในโครังสำรั้างอนาคต้ได้ แต้ไ่ มูจ่ าํ เปุ่็นต้อ้ งเปุ่็นสำว่ นหนึ่งของ Daily Workflow ใน V1
Business Workflow for UX/UI Design Project\n\n-
CLINICERPWORKFLOWBRIEF V1
11. UX/UI Design Challenge
นักศึกึ ษาจะได้รัับ น้ีเปุ่็นขอ้ มููลิต้ัง้ ต้น้ แลิะมูีหน้าท่ีแปุ่ลิง Requirement ใหเ้ ปุ่็นปุ่รัะสำบการัณี์ใช้ง้ านท่ีเหมูาะสำมู
BusinessWorkflow
กับคลิินิกจรัิง
สำงิ่ ท่ีนักศึกึ ษาต้อ้ งเปุ่็นผูู้ต้้ ัดสำนิ ใจเอง
 Information Architecture

NavigationStructure
 จาํ นวนหน้าจอแลิะการัแบง่ Module
 User Flow รัาย์บทบาท
 Wireframe

VisualHierarchy
 Form Design
 Dashboard Visualization
 Error Prevention / Validation
 Responsive UX หากเหน็ วา่ เหมูาะสำมู
สำงิ่ ท่ี Brief น้ีจงใจไมูก่ าํ หนด
 ต้าํ แหน่งเมูนู
 ต้าํ แหน่งปุุ่่มู
 จาํ นวน Click
 รัูปุ่แบบ Calendar
 Layout ของ Checkout
 รัูปุ่แบบ Dashboard
 สำแี ลิะ
BrandStyle
 Wireframe ต้ัวอย์า่ ง
โจทย์ห์ ลิัก
ออกแบบ UX/UI ท่ีทาํ ให ้ Workflow ของ Clinic ERP น้ีใช้ง้ านงา่ ย์ รัวดเรั็ว ลิดความูผู้ดิ พื้ลิาด แลิะทาํ ใหข้ อ้ มููลิท่ีจาํ เปุ่็นต้อ่ การั
บรัิหารัคลิินิกต้รัวจสำอบย์อ้ นหลิังได้
END OF WORKFLOW BRIEF
Business Workflow for UX/UI Design Project