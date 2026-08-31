package com.tskforging.checktagrs

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var db: EvidenceDb
    private lateinit var input: EditText
    private lateinit var panel: LinearLayout
    private lateinit var stepView: TextView
    private lateinit var instruction: TextView
    private lateinit var status: TextView
    private lateinit var employeeView: TextView
    private lateinit var standView: TextView
    private lateinit var boxView: TextView
    private lateinit var kanbanView: TextView
    private lateinit var difference: TextView
    private lateinit var rawButton: Button
    private lateinit var boxDoneButton: Button
    private lateinit var kanbanDoneButton: Button
    private lateinit var rescanButton: Button
    private lateinit var nextButton: Button
    private lateinit var clearButton: Button
    private var flow = MultiKanbanFlow(true, TagParser::partsMatch)
    private val target get() = ScanTarget.valueOf(flow.target.name)
    private var sessionId = ""
    private var sequence = 0
    private var retryCount = 0
    private var checkStand = true
    private var awaitingEmployee = true
    private var cycleComplete = false
    private var employeeName = ""
    private var employeeRaw = ""
    private val standPart get() = flow.standPart
    private val boxes get() = flow.boxes
    private val rawEvents = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        db=EvidenceDb(this); input=findViewById(R.id.scannerInput); panel=findViewById(R.id.resultPanel)
        stepView=findViewById(R.id.step); instruction=findViewById(R.id.instruction); status=findViewById(R.id.status)
        employeeView=findViewById(R.id.employeeName); standView=findViewById(R.id.standPart); boxView=findViewById(R.id.boxPart)
        kanbanView=findViewById(R.id.kanbanPart); difference=findViewById(R.id.difference)
        rawButton=findViewById(R.id.rawButton); boxDoneButton=findViewById(R.id.boxDoneButton)
        kanbanDoneButton=findViewById(R.id.kanbanDoneButton)
        rescanButton=findViewById(R.id.rescanButton); nextButton=findViewById(R.id.nextButton); clearButton=findViewById(R.id.clearButton)
        input.setOnEditorActionListener { _,_,_ -> consumeScan(); true }
        input.setOnKeyListener { _,key,event -> if(key==66 && event.action==1){ consumeScan(); true } else false }
        rawButton.setOnClickListener { showRaw() }; boxDoneButton.setOnClickListener { finishBoxes() }
        kanbanDoneButton.setOnClickListener { finishKanbans() }
        rescanButton.setOnClickListener { resetScanMessage() }; nextButton.setOnClickListener { beginEmployeeScan() }
        clearButton.setOnClickListener { confirmClearLast() }
        findViewById<Button>(R.id.historyButton).setOnClickListener { showHistory() }
        findViewById<Button>(R.id.exportButton).setOnClickListener { exportAndShare() }
        beginEmployeeScan()
    }

    override fun onResume(){ super.onResume(); focusScanner() }

    private fun beginEmployeeScan(){
        if(sessionId.isNotEmpty()) db.cancelSession(sessionId)
        prepareNewCycle()
        showEmployeePrompt()
    }

    private fun prepareNewCycle(){
        sessionId=""; employeeName=""; employeeRaw=""; awaitingEmployee=true; flow=MultiKanbanFlow(true, TagParser::partsMatch)
        cycleComplete=false
        rawEvents.clear(); sequence=0; retryCount=0
    }

    private fun showEmployeePrompt(){
        whitePanel(); status.text="รอสแกนพนักงาน"; stepView.text="SCAN EMPLOYEE"
        instruction.text="สแกน QR พนักงานก่อนเริ่มตรวจ Tag"; employeeView.text="ผู้ตรวจ: —"
        standView.text="STAND\n—"; boxView.text="BOX TAG\n0 ใบ"; kanbanView.text="KANBAN\n0 ใบ"; difference.text=""
        rawButton.visibility=View.GONE; kanbanDoneButton.visibility=View.GONE; boxDoneButton.visibility=View.GONE; rescanButton.visibility=View.GONE; nextButton.visibility=View.GONE; clearButton.visibility=View.GONE
        focusScanner()
    }

    private fun chooseStandMode(){ AlertDialog.Builder(this).setTitle("รายการนี้ต้องตรวจ STAND หรือไม่?")
        .setMessage("เลือกก่อนเริ่มสแกน Tag").setCancelable(false)
        .setPositiveButton("ตรวจ STAND"){_,_->beginSession(true)}.setNegativeButton("ไม่ตรวจ STAND"){_,_->beginSession(false)}.show() }

    private fun beginSession(withStand:Boolean){
        checkStand=withStand; sessionId=UUID.randomUUID().toString(); db.startSession(sessionId,checkStand,employeeName,employeeRaw)
        flow=MultiKanbanFlow(checkStand, TagParser::partsMatch); whitePanel(); status.text="รอการสแกน"
        nextButton.visibility=View.GONE; rescanButton.visibility=View.GONE; rawButton.visibility=View.GONE; difference.text=""
        employeeView.text="ผู้ตรวจ: $employeeName"; standView.text=if(checkStand)"STAND\n—" else "STAND\nข้ามการตรวจ"
        updateCounters(); updateStep(); focusScanner()
    }

    private fun consumeScan(){
        val scanned=input.text.toString(); input.setText(""); if(scanned.isBlank()) return
        if(awaitingEmployee){
            val employee=EmployeeParser.parse(scanned)
            if(employee==null){ showError("QR พนักงานไม่ถูกต้อง", "ต้องเป็น EMPLOYEE|ชื่อ เช่น EMPLOYEE|Mr.Burin"); return }
            if(cycleComplete) prepareNewCycle()
            employeeRaw=employee.raw; employeeName=employee.name; awaitingEmployee=false
            employeeView.text="ผู้ตรวจ: $employeeName ✓"; employeeView.setTextColor(Color.rgb(6,118,71)); chooseStandMode(); return
        }
        if(sessionId.isEmpty()) return
        val parsed=when(target){ ScanTarget.STAND->TagParser.stand(scanned); ScanTarget.BOX_TAG->TagParser.box(scanned); ScanTarget.KANBAN->TagParser.kanban(scanned) }
        sequence++; val eventId=UUID.randomUUID().toString()
        val compare=flow.comparison(if(parsed.success) parsed.partNo else null)
        db.saveEvent(ScanEvidence(eventId,sessionId,sequence,System.currentTimeMillis(),target,scanned,sha256(scanned),parsed.tagType,parsed.partNo,parsed.ruleId,parsed.ruleVersion,if(parsed.success)"SUCCESS" else "INVALID",compare,null))
        if(compare !in setOf("REFERENCE", "MATCH")) retryCount++
        rawEvents += "#$sequence ${target.name}\n$scanned"
        val scannedTarget=target
        if(!flow.accept(if(parsed.success) parsed.partNo else null)){
            boxDoneButton.visibility=View.GONE; kanbanDoneButton.visibility=View.GONE
            if(!parsed.success || parsed.partNo==null) showError("อ่านไม่ได้",parsed.message)
            else {
                val reference=flow.referencePart
                val detail=if(reference!=null) "\nอ้างอิงที่ยังไม่จับคู่: $reference\n${TagParser.firstDifference(reference,parsed.partNo!!)}" else ""
                showError(if(scannedTarget==ScanTarget.KANBAN) "KANBAN ไม่ตรง STAND" else "BOX ไม่มี KANBAN ที่จับคู่ได้",
                    "อ่านได้: ${parsed.partNo}$detail\nไม่นับรายการนี้ กรุณาสแกนใบที่ถูกต้องใหม่" +
                    if(scannedTarget==ScanTarget.BOX_TAG && flow.remaining==0) "\nถ้าสแกนเกินหลังครบแล้ว ให้ล้าง BOX ล่าสุดและสแกนกล่องนั้นใหม่" else "")
            }
            return
        }
        whitePanel(); rescanButton.visibility=View.GONE; rawButton.visibility=View.VISIBLE
        status.text=when(scannedTarget){
            ScanTarget.STAND -> "รับ STAND แล้ว — สแกน KANBAN ให้ครบทุกใบ"
            ScanTarget.KANBAN -> "รับ KANBAN #${flow.kanbans.size} แล้ว — ยิงต่อหรือกด KANBAN ครบ"
            ScanTarget.BOX_TAG -> "BOX #${boxes.size} ตรงกัน"
        }
        status.setTextColor(Color.rgb(6,118,71))
        difference.text=if(scannedTarget==ScanTarget.BOX_TAG) "BOX #${boxes.size}: ${parsed.partNo} ↔ KANBAN #${boxes.last().kanbanScanNo}" else ""
        updateCounters(); updateStep(); focusScanner()
    }

    private fun finishKanbans(){
        if(awaitingEmployee || sessionId.isEmpty()) return
        if(!flow.finishKanbans()){
            Toast.makeText(this,"ต้องมี KANBAN อย่างน้อย 1 ใบ และแก้รายการที่ผิดให้ถูกต้องก่อน",Toast.LENGTH_SHORT).show()
            return
        }
        recordAction("KANBAN_COMPLETE", ScanTarget.KANBAN)
        whitePanel(); status.text="KANBAN ครบ ${flow.kanbans.size} ใบ — เริ่มสแกน BOX"
        difference.text="BOX 1 กล่อง ต่อ KANBAN 1 ใบ"; updateCounters(); updateStep(); focusScanner()
    }

    private fun finishBoxes(){
        if(awaitingEmployee || sessionId.isEmpty()) return
        if(!flow.finish()){
            Toast.makeText(this,if(flow.scanError) "กรุณาสแกนรายการที่ผิดให้ถูกต้องก่อน" else "ต้องจับคู่ BOX กับ KANBAN ให้ครบทุกใบ (เหลือ ${flow.remaining} ใบ)",Toast.LENGTH_SHORT).show()
            return
        }
        showFinal()
    }

    private fun updateCounters(){
        standView.text=if(checkStand) "STAND\n${standPart ?: "—"}" else "STAND\nข้ามการตรวจ"
        boxView.text="BOX TAG\n${boxes.size} กล่องผ่าน"
        kanbanView.text="KANBAN\n${flow.kanbans.size} ใบ • จับคู่ ${boxes.size} • เหลือ ${flow.remaining}" +
            (flow.kanbans.lastOrNull()?.let { "\nล่าสุด: ${it.partNo}" } ?: "")
        boxView.setTextColor(Color.rgb(6,118,71)); kanbanView.setTextColor(Color.rgb(6,118,71))
    }

    private fun showFinal(){
        status.text="OK"; status.setTextColor(Color.WHITE); panel.setBackgroundColor(Color.rgb(3,152,85))
        listOf(employeeView,standView,boxView,kanbanView,difference).forEach{it.setTextColor(Color.WHITE)}
        difference.text="ตรวจผ่าน ${boxes.size} คู่ • BOX ${boxes.size} กล่อง / KANBAN ${flow.kanbans.size} ใบ"
        val parts=mapOfNotNull(ScanTarget.STAND to standPart, ScanTarget.BOX_TAG to boxes.joinToString(" | "){it.partNo}, ScanTarget.KANBAN to flow.kanbans.joinToString(" | "){"#${it.scanNo} ${it.partNo} ↔ BOX #${it.boxScanNo}"})
        db.finishSession(sessionId,"OK",retryCount,parts)
        sessionId=""; awaitingEmployee=true; cycleComplete=true
        stepView.text="COMPARE RESULT"; instruction.text="ตรวจสอบผ่าน • สแกน QR พนักงานเพื่อเริ่มรอบใหม่"
        rawButton.visibility=View.VISIBLE; kanbanDoneButton.visibility=View.GONE; boxDoneButton.visibility=View.GONE; rescanButton.visibility=View.GONE; nextButton.visibility=View.VISIBLE; clearButton.visibility=View.GONE
    }

    private fun mapOfNotNull(vararg pairs:Pair<ScanTarget,String?>):Map<ScanTarget,String> = pairs.mapNotNull{(k,v)->v?.let{k to it}}.toMap()

    private fun showError(title:String,msg:String){ status.text=title; status.setTextColor(Color.WHITE); panel.setBackgroundColor(Color.rgb(217,45,32)); difference.text=msg; difference.setTextColor(Color.WHITE); rescanButton.visibility=View.VISIBLE; rawButton.visibility=View.VISIBLE; focusScanner() }
    private fun resetScanMessage(){
        if(awaitingEmployee){showEmployeePrompt();return}
        if(flow.scanError){instruction.text="สแกนรายการที่ผิดใหม่ให้ถูกต้อง จึงจะทำต่อได้";focusScanner();return}
        whitePanel(); status.text="รอสแกนใหม่"; difference.text=""; rescanButton.visibility=View.GONE
        updateCounters(); updateStep(); focusScanner()
    }

    private fun confirmClearLast(){
        if(awaitingEmployee) return
        val isKanban=target==ScanTarget.KANBAN
        if(if(isKanban) flow.kanbans.isEmpty() else boxes.isEmpty()) return
        val label=if(isKanban) "KANBAN" else "BOX"
        AlertDialog.Builder(this).setTitle("ล้าง $label ล่าสุด?")
            .setMessage(if(isKanban) "ลดจำนวน KANBAN 1 ใบ โดยยังเก็บ RAW DATA" else "ลดจำนวน BOX 1 กล่อง และคืน KANBAN ที่จับคู่ไว้ให้สแกน BOX ใหม่ โดยยังเก็บ RAW DATA")
            .setNegativeButton("ยกเลิก",null).setPositiveButton("ล้าง"){_,_->clearLast()}.show()
    }

    private fun clearLast(){
        val removedTarget=target
        val removedPart=if(target==ScanTarget.KANBAN) flow.kanbans.lastOrNull()?.partNo else boxes.lastOrNull()?.partNo
        if(!flow.removeLast()) return
        recordAction("REMOVE_LAST", removedTarget, removedPart)
        if(!flow.scanError){whitePanel();status.text="ล้างรายการล่าสุดแล้ว";difference.text=""}
        updateCounters(); updateStep(); focusScanner()
    }

    private fun recordAction(action:String, actionTarget:ScanTarget, part:String?=null){
        sequence++
        val raw="USER_ACTION|$action|${actionTarget.name}|${part ?: ""}"
        db.saveEvent(ScanEvidence(UUID.randomUUID().toString(),sessionId,sequence,System.currentTimeMillis(),actionTarget,raw,sha256(raw),"USER_ACTION",part,"batch_operator_action","1.0","ACTION",action,null))
        rawEvents += "#$sequence $raw"
    }

    private fun updateStep(){
        kanbanDoneButton.visibility=if(target==ScanTarget.KANBAN && flow.kanbans.isNotEmpty() && !flow.scanError) View.VISIBLE else View.GONE
        boxDoneButton.visibility=if(flow.canFinish()) View.VISIBLE else View.GONE
        clearButton.visibility=if((target==ScanTarget.KANBAN && flow.kanbans.isNotEmpty()) || (target==ScanTarget.BOX_TAG && boxes.isNotEmpty())) View.VISIBLE else View.GONE
        clearButton.text=if(target==ScanTarget.KANBAN) "ล้าง KANBAN ล่าสุด" else "ล้าง BOX ล่าสุด"
        when(target){
            ScanTarget.STAND->{stepView.text="SCAN STAND";instruction.text="สแกน STAND 1 ใบ"}
            ScanTarget.KANBAN->{stepView.text="SCAN KANBAN • ${flow.kanbans.size} ใบ";instruction.text="สแกน KANBAN ทุกใบ แล้วกด KANBAN ครบ ก่อนเริ่ม BOX"}
            ScanTarget.BOX_TAG->{stepView.text="SCAN BOX TAG • ${boxes.size}/${flow.kanbans.size} กล่อง";instruction.text=if(flow.remaining==0) "จับคู่ครบแล้ว กด BOX ครบ เพื่อจบชุด" else "สแกน BOX จับคู่ 1:1 • ยังเหลือ ${flow.remaining} ใบ"}
        }
        if(flow.scanError) instruction.text="สแกนรายการที่ผิดใหม่ให้ถูกต้องก่อนทำต่อ"
    }

    private fun showRaw()=AlertDialog.Builder(this).setTitle("RAW DATA เต็ม").setMessage(rawEvents.joinToString("\n\n").ifBlank{"—"}).setPositiveButton("ปิด",null).show()

    private fun showHistory(){
        val items=db.history(); if(items.isEmpty()){AlertDialog.Builder(this).setTitle("ประวัติการตรวจ").setMessage("ยังไม่มีข้อมูล").setPositiveButton("ปิด",null).show();return}
        val fmt=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US)
        val labels=items.map{"${fmt.format(Date(it.startedAt))}  ${it.result}\n${it.employeeName.ifBlank{"ไม่ระบุผู้ตรวจ"}} • ${it.partNo} • STAND ${if(it.standMode=="SKIP")"ข้าม" else "ตรวจ"}"}.toTypedArray()
        AlertDialog.Builder(this).setTitle("ประวัติ — เลือกรายการ").setItems(labels){_,i->showHistoryDetail(items[i].sessionId)}.setNegativeButton("ปิด",null).show()
    }
    private fun showHistoryDetail(id:String)=AlertDialog.Builder(this).setTitle("รายละเอียดการตรวจ").setMessage(db.historyDetail(id)).setPositiveButton("ปิด",null).setNeutralButton("กลับไปประวัติ"){_,_->showHistory()}.show()

    private fun exportAndShare(){
        try{
            val timestamp=SimpleDateFormat("yyyy-MM-dd_HHmmss",Locale.US).format(Date()); val file=db.exportCsv(File(cacheDir,"exports/CheckTag_RS_$timestamp.csv"))
            val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
            val send=Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_EMAIL,arrayOf("wirachai.so@tskforging.com","sart.ka@tskforging.com"));putExtra(Intent.EXTRA_STREAM,uri);putExtra(Intent.EXTRA_SUBJECT,"Check Tag_RS report $timestamp");putExtra(Intent.EXTRA_TEXT,"รายงานผลตรวจสอบ Tag จาก PM75 (OK/NG)\nไฟล์: ${file.name}");clipData=ClipData.newRawUri(file.name,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            try{startActivity(Intent(send).apply{setPackage("com.microsoft.office.outlook")})}catch(_:Exception){startActivity(Intent.createChooser(send,"เลือกแอปอีเมลเพื่อส่ง CSV"))}
        }catch(e:Exception){Toast.makeText(this,"ส่งออกไม่สำเร็จ: ${e.message}",Toast.LENGTH_LONG).show()}
    }

    private fun whitePanel(){ panel.setBackgroundColor(Color.WHITE); listOf(employeeView,standView,boxView,kanbanView,difference).forEach{it.setTextColor(Color.rgb(16,24,40))}; status.setTextColor(Color.rgb(52,64,84)); clearButton.visibility=View.VISIBLE }
    private fun focusScanner(){input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)}
    private fun sha256(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
}
