# Check Tag_RS v0.22.2

## Optional Pick List comparison (v0.22.2)

- หลัง Scan พนักงาน ให้เลือก `เปรียบเทียบ` หรือ `ไม่เปรียบเทียบ` Pick List กับ KANBAN
- เลือกเปรียบเทียบ: Scan Pick List Aisin แล้วจึง Scan KANBAN
- เลือกไม่เปรียบเทียบ: ข้ามไป Scan KANBAN ทันที
- หน้าแถบขั้นตอนแสดง `ข้าม Pick List` อย่างชัดเจนเมื่อเลือกไม่ตรวจ
- QR Delivery Order, Stand, Box, Dashboard และกฎ WARNING/MISMATCH ยังทำงานเหมือน v0.22.1
- versionCode 30 / versionName 0.22.2

## Test hotfix (v0.22.1)

- แก้ DNTH ไม่ให้ตัวเลขจำนวน 7 หลักที่อยู่ถัดจาก Part No. ถูกต่อเป็นส่วนหนึ่งของ Part No.
- รักษาความเข้ากันได้ของ Flow tests เดิม โดยหน้าจอใช้งานจริงยังตัดสินด้วย EXACT/WARNING/MISMATCH
- เพิ่มการตรวจ DNTH แถวบนแบบตัวอักษรยาวที่พิมพ์ติดกับจำนวน
- versionCode 29 / versionName 0.22.1

## Part comparison and DNTH/JTCS update (v0.22.0)

- เปรียบเทียบ Part No. เต็มหลังตัดช่องว่างและปรับตัวพิมพ์ใหญ่/เล็ก
- รหัสเต็มตรงกันเป็น `OK`
- รหัสก่อนขีดแรกตรง แต่รายละเอียดหลังขีดต่างเป็น `WARNING` และต้องกรอกเหตุผลเพื่อทำต่อ
- รหัสก่อนขีดแรกต่างเป็น `MISMATCH` และต้องสแกนใหม่
- DNTH DISC ยึดรหัส `TG/TGY` แถวล่างก่อน `01` เป็นหลัก โดยไม่อิง `C07`, `T1`, `T-1`, `T 1` หรือจำนวนช่องว่าง
- รองรับ KANBAN JTCS ที่ `B01` ติดกับ Part No. เช่น `B01JGD10-0001230-40...`
- WARNING ถูกเก็บในประวัติ/RAW DATA และทำให้ผลสุดท้ายเป็น WARNING แม้จำนวน Box ตรง
- versionCode 28 / versionName 0.22.0

Flow: พนักงาน → เลือกตรวจ/ข้าม Pick List → KANBAN → QR Delivery Order → Stand → Box ทุกกล่อง → BOX ครบ → Dashboard → ตรวจและส่ง Mail

กฎ Aisin จากข้อมูลจริง:
- JCC ใน KANBAN ต้องตรงกับด้านหน้าของ JCC ใน Pick List
- ต้องพบรหัสร่วม `J631` และ `7D42`
- Part No. ใช้ค่าจาก KANBAN เช่น `0116171-05030` → `16171-05030`
- ไม่สนใจช่องว่างปกติ/Unicode และเก็บ RAW DATA เต็ม

จำนวน Box:
- กรอกจำนวนงาน (PCS) และจำนวน Box ที่ต้องส่ง
- นับเฉพาะ Box ที่ตรงกับ KANBAN และ Stand
- จำนวนตรงบันทึก `OK`
- ขาด/เกินต้องกรอกเหตุผลก่อนยืนยัน และบันทึก `WARNING`

Dashboard บน PM75 แสดงผู้ตรวจ, Part No., จำนวนงาน, Box กำหนด/Scan จริง, ผลต่าง และเหตุผลก่อนเปิด Outlook
CSV/ประวัติเพิ่ม Pick List mode, JCC, จำนวนงาน, จำนวน Box, ผลต่าง และเหตุผล
ผู้รับ Mail: `wirachai.so@tskforging.com`, `sart.ka@tskforging.com`, `arnon.ju@tskforging.com`

ฐานข้อมูลอัปเกรดจาก schema 4 เป็น 5 โดยไม่ลบประวัติเดิม ลูกค้าอื่นยังเลือกข้าม Pick List ได้จนกว่าจะมีตัวอย่างจริง

อัปโหลดไฟล์ภายในโฟลเดอร์ไป root ของ repository แล้วรัน GitHub Actions ต้องผ่าน `testDebugUnitTest assembleDebug` ก่อนดาวน์โหลด Artifact `Check_Tag_RS_v0.17_APK` ซึ่งมี `Check_Tag_RS_v0.17.apk`

Hotfix v0.17.1: รักษาตัวคั่นของช่อง JCC เพื่อไม่ให้เลขช่องถัดไปถูกต่อรวมกับเลขอ้างอิง และยังรองรับช่องว่าง Unicode

v0.17.2:
- เตือนทันทีเมื่อ Scan Box เกินจำนวนที่กำหนด
- เลือกลบ Box ล่าสุดหรือเก็บ Box ที่เกินไว้เพื่อยืนยันส่งภายหลัง
- เปิดปุ่มลบเมื่อมีข้อมูลที่ลบได้ และปิดเมื่อไม่มีข้อมูล
- เพิ่มแถบลำดับ `พนักงาน › Pick List › KANBAN › จำนวน › Stand › Box › Dashboard`
- ทำเครื่องหมายขั้นที่ผ่านแล้ว ขั้นปัจจุบัน และบอกขั้นถัดไปเป็นข้อความชัดเจน

versionCode 24 / versionName 0.18.2 / applicationId com.tskforging.checktagrs

## DNTH DISC long-suffix update (v0.18.2)

- อ่าน Part No. แถวล่างก่อน `01` เป็นจุดอ้างอิงของโครงสร้าง KANBAN
- รองรับ Part No. DNTH ที่ส่วนหลัง `-` ยาว 4–10 ตัว เช่น `TG053661-7020S2`
- รองรับจำนวน 7 หลักที่พิมพ์ต่อชิด Part No. เช่น `TG053661-7020S20000100`
- ตรวจว่า Part No. ด้านบนตรงกับรหัสแถวล่างก่อนยอมรับ
- กรณี DISC สองรหัสแบบเดิม ยังเลือกรหัส Box ก่อนจำนวนตามกฎเดิม
- Stand และ Box รองรับ Part No. DNTH suffix ยาวเหมือน KANBAN

## Central Dashboard / Google Sheets (v0.18)

- ส่งชุดงานที่จบแล้วไปยัง Google Apps Script endpoint ของ Check Tag_RS
- ส่งทั้งข้อมูลสรุป `Sessions` และหลักฐาน `Scan Events` รวม RAW DATA
- ใช้ `session_id` เดิมทุกครั้ง เพื่อให้ฝั่ง Apps Script ป้องกันรายการซ้ำ
- บันทึกสถานะใน SQLite: `PENDING`, `SENDING`, `SYNCED`
- ใช้ WorkManager รอเครือข่ายและส่งคิวซ้ำอัตโนมัติเมื่อ PM75 กลับมาออนไลน์
- Dashboard บน PM75 แสดงสถานะข้อมูลกลางก่อนเปิด Outlook
- ถ้ายัง Sync ไม่สำเร็จ ผู้ใช้จะได้รับคำเตือนและเลือกว่าจะรอก่อนหรือเปิด Outlook ต่อ
- Endpoint: `https://script.google.com/macros/s/AKfycbxS-jaE6QBM_JE3UbuNRk3ighMvtPsUTGyeleMYkM4oUAK0Kh05yS6EU7kDOtqUn_3Ziw/exec`
ยังไม่ยืนยัน Android/Kotlin build หรือทดสอบบน PM75 จนกว่า GitHub Actions จะผ่าน
# Check Tag_RS Android v0.19.0

## Delivery Order QR preset

- หลัง Scan KANBAN เลือก `Scan QR Delivery Order` หรือ `กรอกเอง`
- QR รูปแบบ `CHECKTAGRS|DO|PART=...|QTY=...|BOX=...`
- เติม Current QTY และ NO. OF BOX อัตโนมัติ
- ตรวจ Part No. ใน QR กับ KANBAN ก่อนรับจำนวน
- ไม่รับ QTY หรือ BOX ที่เป็นศูนย์
- วิธีกรอกจำนวนแบบเดิมยังคงใช้งานได้

## DNTH Lane update (v0.20.0)

- KANBAN แบบ DISC รองรับช่อง Lane ทั้ง `T1`, `T-1` และ `T 1`
- ยังคงตรวจ Part No. แถวบนและแถวล่าง รวมทั้งโครงสร้าง C07 เหมือนเดิม
- versionCode 26 / versionName 0.20.0

## Mandatory scan workflow (v0.21.0)

- ลำดับบังคับ: พนักงาน → Pick List Aisin → KANBAN → QR Delivery Order → Stand → Box → Dashboard
- ยกเลิกหน้าต่างเลือกตรวจหรือข้าม Pick List
- ยกเลิกการกรอกจำนวนงานและจำนวน Box ด้วยแป้นพิมพ์
- จำนวนงานและจำนวน Box ต้องมาจาก QR Delivery Order เท่านั้น
- versionCode 27 / versionName 0.21.0
- Hotfix: คืน `InputType` import สำหรับช่องกรอกเหตุผลเมื่อจำนวน Box ไม่ตรง
