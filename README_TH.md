# Check Tag_RS v0.18.0

Flow: พนักงาน → เลือกตรวจ/ข้าม Pick List → Pick List Aisin → KANBAN → กรอกจำนวนงานและจำนวน Box → Stand → Box ทุกกล่อง → BOX ครบ → Dashboard → ตรวจและส่ง Mail

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

versionCode 22 / versionName 0.18.0 / applicationId com.tskforging.checktagrs

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
