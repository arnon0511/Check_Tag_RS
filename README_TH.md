# Check Tag_RS v0.17.0

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

versionCode 19 / versionName 0.17.0 / applicationId com.tskforging.checktagrs
ยังไม่ยืนยัน Android/Kotlin build หรือทดสอบบน PM75 จนกว่า GitHub Actions จะผ่าน
