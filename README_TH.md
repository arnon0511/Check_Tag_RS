# Check Tag_RS v0.15.0 — KANBAN หลายใบก่อน BOX

แก้จาก v0.14 ตามลำดับล่าสุดที่ตกลงกัน:

1. สแกนพนักงาน 1 ครั้ง แล้วเลือกตรวจ/ข้าม STAND
2. สแกน STAND 1 ใบ ถ้าเลือกตรวจ
3. สแกน KANBAN ทุกใบต่อเนื่อง ตัวนับเพิ่มทุกใบที่ผ่าน และยังไม่เข้าสู่ BOX
4. กด **KANBAN ครบ / เริ่มสแกน BOX**
5. สแกน Tag Box ทีละกล่อง จับคู่ Part No. กับ KANBAN ที่ยังว่างอัตโนมัติ **1:1**
6. เมื่อจับคู่ครบทุกใบ กด **BOX ครบ / จบชุด** แล้วจึงสแกนพนักงานรอบใหม่

ตัวอย่าง: KANBAN 3 ใบ → KANBAN ครบ → BOX 3 กล่อง → BOX ครบ
ไม่ใช้ KANBAN ใบเดียวตรวจหลายกล่อง และไม่สลับ KANBAN1 → BOX1 → KANBAN2 → BOX2

## การตรวจและแก้รายการ

- KANBAN ทุกใบต้องตรง STAND เมื่อเปิดตรวจ STAND
- BOX ต้องตรงกับ STAND และ KANBAN ที่ยังไม่จับคู่; ใช้ KANBAN หนึ่งรายการได้เพียงครั้งเดียว
- โหมดข้าม STAND จับคู่ตาม Part No. ได้แม้สแกน BOX คนละลำดับกับ KANBAN
- ไม่แสดงปุ่ม BOX ครบจนจำนวนจับคู่ครบ และไม่มีการสแกนผิดที่ค้างอยู่
- BOX ไม่ตรง/เกิน หรือข้อมูลอ่านไม่ได้: หน้าจอแดง ไม่เพิ่มจำนวน เก็บ RAW DATA และต้องสแกนแก้ให้ถูกต้อง
- ระหว่างเก็บ KANBAN สามารถล้าง KANBAN ล่าสุดได้ ก่อนกด KANBAN ครบ
- ระหว่างเก็บ BOX สามารถล้าง BOX ล่าสุดได้ และ KANBAN คู่ของกล่องนั้นจะกลับมาว่าง
- การล้างรายการไม่ปิดข้อผิดพลาดโดยอัตโนมัติ ต้องสแกนรายการที่ถูกต้องใหม่
- หากเผลอสแกนเกินหลังจับคู่ครบแล้ว ให้ล้าง BOX ล่าสุดและสแกนกล่องที่ล้างนั้นใหม่ ก่อนกด BOX ครบ
- นับตามรายการที่สแกนผ่าน ไม่ได้เพิ่มระบบตรวจ serial ของฉลากซ้ำในรุ่นนี้; Item เดียวกันต่าง Lot ยังคงสแกนได้
- หน้าจอแสดงจำนวน KANBAN / คู่ที่ผ่าน / ที่ยังเหลือ และหมายเลขคู่ BOX ↔ KANBAN

## DNTH และกติกาเดิม

คง parser ที่แก้ใน v0.14 โดยไม่เปลี่ยนไบต์:
- Stand/Box รับรหัสตรง ๆ `TG028993-590 A` โดยไม่ต้องมี `|`
- รองรับช่องว่างปกติและ Unicode, `|PART-NO`, FG Tag ช่องที่ 4, และ Box แบบ I-prefix
- DNTH DISC แบบใหม่เลือก `TG028993-590A` ไม่ใช่รหัสอ้างอิงแรก `TG028382-502C`
- DNTH เปรียบเทียบเต็มรวม suffix; JTEKT คงกฎก่อนขีดแรก; คง Aisin และ SNSS

## ประวัติและ CSV

คงฐานข้อมูลและผู้รับเดิม ไม่มีการลบข้อมูลหรือเปลี่ยน schema:
- wirachai.so@tskforging.com
- sart.ka@tskforging.com

บันทึก RAW ทุกครั้ง รวมรายการผิด และเพิ่มหลักฐานเมื่อกด KANBAN ครบหรือล้างรายการ
ประวัติ/CSV เก็บ KANBAN ทุกใบพร้อมหมายเลข BOX ที่จับคู่

**งานเก่าที่ยังไม่รวม:** ขั้นตอนอนุมัติส่งงานผิดพร้อมเหตุผลและการยืนยันส่ง Outlook จริงก่อนทำต่อ
รุ่นนี้ยังไม่อนุญาตข้ามผลไม่ตรงเป็นผ่าน ปุ่มส่ง CSV เปิด Outlook ให้ผู้ใช้ส่งเอง และไม่ได้ยืนยันการส่งจากเซิร์ฟเวอร์

## ทดสอบที่ทำแล้ว

- Compile และรัน `MultiKanbanFlow.java` ซึ่งเป็นตัวควบคุมที่แอปเรียกใช้จริง ผ่าน 18 กรณีด้วย Java 17
- ตรวจ XML ทั้ง 4 ไฟล์ และการอ้างอิง ID หน้าจอ 18 รายการ
- ตรวจว่า parser DNTH/JTEKT/Aisin/SNSS และ EmployeeParser คงเดิมทุกไบต์
- ดูผลใน `VALIDATION.txt`

**ยังไม่ได้ทำ:** compile Kotlin/Android ทั้งแอป, รัน JUnit ทั้งชุด, สร้าง APK และทดสอบ PM75/โทรศัพท์จริง
สภาพแวดล้อมนี้ไม่มี Kotlin compiler, Gradle หรือ Android SDK และดาวน์โหลดเครื่องมือไม่สำเร็จ
ผลทดสอบ 18 กรณีเป็นผลทดสอบตัวควบคุมลำดับ ไม่ใช่ผล build Android หรือผลทดสอบ parser Kotlin
มี JUnit สำหรับรวม parser จริงกับลำดับใหม่เตรียมไว้ให้ GitHub Actions รัน

## สร้าง APK ผ่าน GitHub Actions

1. แตก ZIP นี้ แล้วอัปโหลด **ไฟล์ด้านใน** โฟลเดอร์ `Check_Tag_RS_Android_v0.15` ทับ root ของ repository Check-Tag-RS
2. ต้องมีไฟล์ใหม่ `MultiKanbanFlow.java`, `MultiKanbanFlowChecks.java`, `MultiKanbanFlowTest.kt` และ `.github/workflows/build-apk.yml`
3. ลบไฟล์เก่า `app/src/main/java/com/tskforging/checktagrs/SingleKanbanFlow.kt` และ `app/src/test/java/com/tskforging/checktagrs/SingleKanbanFlowTest.kt` หากยังมีใน repository
4. เปิด GitHub → Actions → Build Check Tag_RS APK → Run workflow
5. Workflow ต้องผ่าน `testDebugUnitTest assembleDebug` ก่อนดาวน์โหลด Artifact `Check_Tag_RS_v0.15_APK`
6. APK ภายในชื่อ `Check_Tag_RS_v0.15.apk`

ยังไม่ได้อัปโหลดหรือสั่ง build ใน GitHub จากแชตนี้ เพราะไม่มีการเชื่อมต่อ repository
versionCode 17 / versionName 0.15.0 / applicationId com.tskforging.checktagrs
สำรอง CSV ก่อนอัปเดต; ถ้าลายเซ็นไม่ตรงอย่าเพิ่งถอนแอป เพราะประวัติในเครื่องอาจหาย

## ทดสอบหน้างานหลัง build ผ่าน

สแกน `EMPLOYEE|Mr.Burin` → เลือกตรวจ STAND → สแกน `TG028993-590 A`
สแกน KANBAN ของ Part นี้ 3 ใบ → ตัวนับต้องเป็น 3 และยังอยู่หน้า KANBAN
กด KANBAN ครบ → สแกน BOX 2 กล่อง → ยังจบไม่ได้
สแกน BOX ที่ 3 → แสดงครบ 3 คู่ → กด BOX ครบ → รอสแกนพนักงานใหม่
ลอง BOX ผิด suffix `TG028993-590B` ต้องแดงและไม่นับ; ตรวจประวัติและ CSV ให้ครบ

ตัวอย่าง KANBAN จากผู้ใช้ (สำหรับทดสอบ parser):
```text
DISC5060020000010101000210125104151120710725124061290515207154081550911TG028382-502C            TG028993-590A 0000040                         C07        3001660                 T-5          6082501901TG028382-502C       01
```

## รันชุดตรวจลำดับโดยไม่ใช้ Android SDK

ใช้ Java 17 ที่มี compiler module จาก root โปรเจกต์:
```sh
java tools/RunFlowChecks.java
```
