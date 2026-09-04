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
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private enum class Stage { EMPLOYEE, PICK_LIST, KANBAN, DELIVERY_ORDER, STAND, BOX, DASHBOARD }
    private lateinit var db:EvidenceDb; private lateinit var input:EditText; private lateinit var panel:LinearLayout
    private lateinit var stepView:TextView; private lateinit var instruction:TextView; private lateinit var status:TextView
    private lateinit var flowBar:TextView
    private lateinit var employeeView:TextView; private lateinit var standView:TextView; private lateinit var boxView:TextView
    private lateinit var kanbanView:TextView; private lateinit var difference:TextView; private lateinit var rawButton:Button
    private lateinit var boxDoneButton:Button; private lateinit var resetBatchButton:Button; private lateinit var rescanButton:Button
    private lateinit var nextButton:Button; private lateinit var clearButton:Button
    private var stage=Stage.EMPLOYEE; private var sessionId=""; private var sequence=0; private var retryCount=0
    private var employeeName=""; private var employeeRaw=""; private var comparePick=true; private var pickRaw=""
    private var pickMatch:AisinDocumentMatch?=null; private var kanbanRaw=""; private var kanbanPart=""
    private var standPart=""; private var workQty=0; private var expectedBoxes=0; private val boxes=mutableListOf<String>()
    private var pendingError=false; private var overrideReason=""; private val rawEvents=mutableListOf<String>()

    override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState);setContentView(R.layout.activity_main)
        db=EvidenceDb(this);input=findViewById(R.id.scannerInput);panel=findViewById(R.id.resultPanel);stepView=findViewById(R.id.step)
        instruction=findViewById(R.id.instruction);flowBar=findViewById(R.id.flowBar);status=findViewById(R.id.status);employeeView=findViewById(R.id.employeeName)
        standView=findViewById(R.id.standPart);boxView=findViewById(R.id.boxPart);kanbanView=findViewById(R.id.kanbanPart)
        difference=findViewById(R.id.difference);rawButton=findViewById(R.id.rawButton);boxDoneButton=findViewById(R.id.boxDoneButton)
        resetBatchButton=findViewById(R.id.resetBatchButton);rescanButton=findViewById(R.id.rescanButton);nextButton=findViewById(R.id.nextButton);clearButton=findViewById(R.id.clearButton)
        input.setOnEditorActionListener{_,_,_->consumeScan();true};input.setOnKeyListener{_,k,e->if(k==66&&e.action==1){consumeScan();true}else false}
        rawButton.setOnClickListener{showRaw()};boxDoneButton.setOnClickListener{finishBoxes()};resetBatchButton.setOnClickListener{confirmReset()}
        rescanButton.setOnClickListener{focusScanner()};nextButton.setOnClickListener{resetAll()};clearButton.setOnClickListener{clearLast()}
        findViewById<Button>(R.id.historyButton).setOnClickListener{showHistory()};findViewById<Button>(R.id.exportButton).setOnClickListener{exportAndShare()};resetAll()
    }
    override fun onResume(){super.onResume();if(db.pendingSyncIds().isNotEmpty())enqueueCentralSync();focusScanner()}
    private fun consumeScan(){val raw=input.text.toString();input.setText("");if(raw.isBlank())return
        when(stage){
            Stage.EMPLOYEE->{val e=EmployeeParser.parse(raw)?:return showError("QR พนักงานไม่ถูกต้อง","ต้องเป็น EMPLOYEE|ชื่อ")
                employeeName=e.name;employeeRaw=e.raw;comparePick=true;sessionId=UUID.randomUUID().toString();db.startSession(sessionId,true,employeeName,employeeRaw);employeeView.text="ผู้ตรวจ: $employeeName ✓";stage=Stage.PICK_LIST;showNormal("รอ Scan Pick List");updateUi()}
            Stage.PICK_LIST->{if(!Regex("JCC\\d{11,}",RegexOption.IGNORE_CASE).containsMatchIn(raw.filterNot{it.isWhitespace()}))return showError("อ่าน Pick List ไม่ได้","ไม่พบเลขอ้างอิง JCC")
                pickRaw=raw;saveEvidence(ScanTarget.PICK_LIST,raw,"PICK_LIST_AISIN",null,"REFERENCE");stage=Stage.KANBAN;showNormal("รับ Pick List แล้ว");updateUi()}
            Stage.KANBAN->{val p=TagParser.kanban(raw);if(!p.success||p.partNo==null)return reject(ScanTarget.KANBAN,raw,p,"อ่าน KANBAN ไม่ได้")
                if(comparePick){val m=AisinPickListMatcher.compare(pickRaw,raw);if(!m.success){pickMatch=m;return reject(ScanTarget.KANBAN,raw,p,"Pick List ไม่ตรง KANBAN\n${m.message}")};pickMatch=m}
                kanbanRaw=raw;kanbanPart=p.partNo;saveEvidence(ScanTarget.KANBAN,raw,p.tagType,p.partNo,"MATCH");stage=Stage.DELIVERY_ORDER;showNormal("KANBAN ตรง — รอ Scan QR Delivery Order");updateUi()}
            Stage.DELIVERY_ORDER->{val preset=DeliveryOrderQrParser.parse(raw)
                if(preset==null)return showError("QR Delivery Order ไม่ถูกต้อง","ต้องมี Part No., Current QTY และ NO. OF BOX มากกว่า 0")
                if(!TagParser.partsMatch(kanbanPart,preset.partNo))return showError("Delivery Order ไม่ตรง KANBAN","DO: ${preset.partNo}\nKANBAN: $kanbanPart")
                workQty=preset.currentQty;expectedBoxes=preset.numberOfBoxes
                db.saveInspectionDetails(sessionId,comparePick,pickRaw,pickMatch?.pickJcc,pickMatch?.kanbanJcc,workQty,expectedBoxes)
                rawEvents+="#DO ${preset.partNo} | QTY=${preset.currentQty} | BOX=${preset.numberOfBoxes}"
                pendingError=false;stage=Stage.STAND;showNormal("รับจำนวนจาก Delivery Order แล้ว");updateUi()}
            Stage.STAND->{val p=TagParser.stand(raw);if(!p.success||p.partNo==null)return reject(ScanTarget.STAND,raw,p,"อ่าน Stand ไม่ได้")
                if(!TagParser.partsMatch(kanbanPart,p.partNo))return reject(ScanTarget.STAND,raw,p,"Stand ไม่ตรง KANBAN")
                standPart=p.partNo;saveEvidence(ScanTarget.STAND,raw,p.tagType,p.partNo,"MATCH");pendingError=false;stage=Stage.BOX;showNormal("Stand ตรง — เริ่ม Scan Box");updateUi()}
            Stage.BOX->{val p=TagParser.box(raw);if(!p.success||p.partNo==null)return reject(ScanTarget.BOX_TAG,raw,p,"อ่าน Tag Box ไม่ได้")
                if(!TagParser.partsMatch(kanbanPart,p.partNo)||!TagParser.partsMatch(standPart,p.partNo))return reject(ScanTarget.BOX_TAG,raw,p,"Box ไม่ตรงกับ KANBAN/Stand")
                boxes+=p.partNo;saveEvidence(ScanTarget.BOX_TAG,raw,p.tagType,p.partNo,"MATCH");pendingError=false;showNormal("BOX #${boxes.size} ผ่าน");updateUi();if(expectedBoxes>0&&BoxCountEvaluator.status(expectedBoxes,boxes.size)==BoxCountStatus.OVER)showOverCountWarning()}
            else->Unit
        };focusScanner()
    }
    private fun finishBoxes(){if(stage!=Stage.BOX||boxes.isEmpty()||pendingError)return Toast.makeText(this,"ต้องมี Box ผ่านและแก้รายการผิดก่อน",Toast.LENGTH_SHORT).show()
        if(boxes.size==expectedBoxes)return complete("OK","")
        val edit=EditText(this).apply{hint="เหตุผลที่ยืนยันส่ง";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE}
        val d=AlertDialog.Builder(this).setTitle("จำนวน Box ไม่ตรง").setMessage("กำหนด $expectedBoxes Box / Scan ${boxes.size} Box\n${boxDifference()}").setView(edit).setNegativeButton("กลับไป Scan เพิ่ม",null).setPositiveButton("ยืนยันส่ง",null).create()
        d.setOnShowListener{d.getButton(-1).setOnClickListener{if(edit.text.toString().isBlank())Toast.makeText(this,"ต้องกรอกเหตุผล",Toast.LENGTH_SHORT).show()else{overrideReason=edit.text.toString();d.dismiss();complete("WARNING",overrideReason)}}};d.show()}
    private fun showOverCountWarning(){
        AlertDialog.Builder(this).setTitle("⚠ Scan Box เกินจำนวน")
            .setMessage("กำหนด $expectedBoxes Box แต่ Scan แล้ว ${boxes.size} Box\nเกิน ${boxes.size-expectedBoxes} Box\n\nหากยิงซ้ำให้ลบ Box ล่าสุด หากต้องส่งเกินให้เก็บไว้และกด BOX ครบเพื่อระบุเหตุผล")
            .setCancelable(false)
            .setNegativeButton("ลบ Box ล่าสุด"){_,_->if(boxes.isNotEmpty())boxes.removeAt(boxes.lastIndex);pendingError=false;showNormal("ลบ Box ล่าสุดแล้ว");updateUi();focusScanner()}
            .setPositiveButton("เก็บ Box ที่เกินไว้"){_,_->showError("จำนวน Box เกิน","เกิน ${boxes.size-expectedBoxes} Box • กดลบหากยิงซ้ำ หรือกด BOX ครบเพื่อยืนยันส่ง")}
            .show()
    }
    private fun complete(result:String,reason:String){stage=Stage.DASHBOARD;val parts=mapOf(ScanTarget.STAND to standPart,ScanTarget.KANBAN to kanbanPart,ScanTarget.BOX_TAG to boxes.joinToString(" | "))
        db.finishSession(sessionId,result,retryCount,parts,boxes.size,reason);showDashboard(result);enqueueCentralSync()}
    private fun showDashboard(result:String){whitePanel();status.text=if(result=="OK")"OK — พร้อมส่ง" else "WARNING — ยืนยันส่ง";status.setTextColor(Color.WHITE);panel.setBackgroundColor(if(result=="OK")Color.rgb(3,152,85)else Color.rgb(245,158,11))
        employeeView.text="ผู้ตรวจ: $employeeName";kanbanView.text="Part No.\n$kanbanPart";standView.text="จำนวนงาน\n$workQty PCS";boxView.text="BOX\nกำหนด $expectedBoxes / Scan ${boxes.size}";difference.text=boxDifference()+(if(overrideReason.isNotBlank())"\nเหตุผล: $overrideReason" else "")+"\nข้อมูลกลาง: "+syncLabel(db.syncStatus(sessionId))
        listOf(employeeView,kanbanView,standView,boxView,difference).forEach{it.setTextColor(Color.WHITE)};stepView.text="DASHBOARD";instruction.text="ขั้นถัดไป: ตรวจข้อมูลและส่ง Mail";updateFlowBar();boxDoneButton.visibility=View.GONE;clearButton.visibility=View.GONE;rescanButton.visibility=View.GONE;nextButton.visibility=View.VISIBLE;nextButton.text="เริ่มชุดใหม่";rawButton.visibility=View.VISIBLE;findViewById<Button>(R.id.exportButton).text="ตรวจและส่ง Mail"}
    private fun boxDifference()=when(BoxCountEvaluator.status(expectedBoxes,boxes.size)){BoxCountStatus.MATCH->"จำนวน Box ตรงกัน";BoxCountStatus.UNDER->"ขาด ${expectedBoxes-boxes.size} Box";BoxCountStatus.OVER->"เกิน ${boxes.size-expectedBoxes} Box"}
    private fun updateUi(){whitePanel();employeeView.text="ผู้ตรวจ: ${employeeName.ifBlank{"—"}}";kanbanView.text="KANBAN\n${kanbanPart.ifBlank{"—"}}";standView.text="STAND\n${standPart.ifBlank{"—"}}";boxView.text="BOX TAG\n${boxes.size} / ${if(expectedBoxes>0)expectedBoxes else "—"}";difference.text=if(expectedBoxes>0)boxDifference()else ""
        stepView.text=when(stage){Stage.PICK_LIST->"SCAN PICK LIST";Stage.KANBAN->"SCAN KANBAN";Stage.DELIVERY_ORDER->"SCAN DELIVERY ORDER";Stage.STAND->"SCAN STAND";Stage.BOX->"SCAN BOX • ${boxes.size}";else->"CHECK TAG_RS"}
        instruction.text=when(stage){Stage.PICK_LIST->"ตอนนี้: Scan Pick List Aisin • ถัดไป: KANBAN";Stage.KANBAN->"ตอนนี้: Scan KANBAN 1 ใบ • ถัดไป: Scan QR Delivery Order";Stage.DELIVERY_ORDER->"ตอนนี้: Scan QR Delivery Order • ถัดไป: Stand";Stage.STAND->"ตอนนี้: Scan Stand • ถัดไป: Box";Stage.BOX->"ตอนนี้: Scan Box ทุกกล่อง • ถัดไป: Dashboard";else->""};updateFlowBar();boxDoneButton.visibility=if(stage==Stage.BOX&&boxes.isNotEmpty())View.VISIBLE else View.GONE;clearButton.visibility=if(stage in listOf(Stage.PICK_LIST,Stage.KANBAN,Stage.DELIVERY_ORDER,Stage.STAND,Stage.BOX))View.VISIBLE else View.GONE;clearButton.isEnabled=stage==Stage.BOX&&boxes.isNotEmpty()||stage==Stage.STAND&&kanbanPart.isNotBlank()||stage==Stage.DELIVERY_ORDER||stage==Stage.KANBAN&&pickRaw.isNotBlank();rawButton.visibility=if(rawEvents.isNotEmpty())View.VISIBLE else View.GONE;nextButton.visibility=View.GONE;rescanButton.visibility=View.GONE;resetBatchButton.visibility=if(stage==Stage.EMPLOYEE)View.GONE else View.VISIBLE}
    private fun updateFlowBar(){
        val names=listOf(Stage.EMPLOYEE to "พนักงาน",Stage.PICK_LIST to "Pick List",Stage.KANBAN to "KANBAN",Stage.DELIVERY_ORDER to "DO/จำนวน",Stage.STAND to "Stand",Stage.BOX to "Box",Stage.DASHBOARD to "Dashboard")
        val current=names.indexOfFirst{it.first==stage};flowBar.text=names.mapIndexed{i,p->when{i<current->"✓${p.second}";i==current->"[${p.second}]";else->p.second}}.joinToString("  ›  ")
    }
    private fun clearLast(){when{stage==Stage.BOX&&boxes.isNotEmpty()->boxes.removeAt(boxes.lastIndex);stage==Stage.BOX->{standPart="";stage=Stage.STAND};stage==Stage.STAND->{workQty=0;expectedBoxes=0;stage=Stage.DELIVERY_ORDER};stage==Stage.DELIVERY_ORDER->{kanbanPart="";kanbanRaw="";stage=Stage.KANBAN};stage==Stage.KANBAN->{pickRaw="";pickMatch=null;stage=Stage.PICK_LIST};else->return};pendingError=false;updateUi()}
    private fun reject(t:ScanTarget,raw:String,p:ParseResult,title:String){retryCount++;pendingError=true;saveEvidence(t,raw,p.tagType,p.partNo,"MISMATCH");showError(title,p.message.ifBlank{"กรุณาตรวจและ Scan ใหม่"})}
    private fun saveEvidence(t:ScanTarget,raw:String,type:String,part:String?,compare:String){sequence++;db.saveEvent(ScanEvidence(UUID.randomUUID().toString(),sessionId,sequence,System.currentTimeMillis(),t,raw,sha256(raw),type,part,"v018_central_sync","1.0","SUCCESS",compare,null));rawEvents+="#$sequence ${t.name}\n$raw"}
    private fun showError(title:String,msg:String){status.text=title;status.setTextColor(Color.WHITE);panel.setBackgroundColor(Color.rgb(217,45,32));difference.text=msg;difference.setTextColor(Color.WHITE);rescanButton.visibility=View.VISIBLE;rawButton.visibility=View.VISIBLE;focusScanner()}
    private fun showNormal(text:String){whitePanel();status.text=text;status.setTextColor(Color.rgb(6,118,71));difference.text=""}
    private fun confirmReset(){AlertDialog.Builder(this).setTitle("ล้างชุดปัจจุบัน?").setMessage("ข้อมูลชุดนี้จะถูกยกเลิก แต่ RAW DATA ยังอยู่").setNegativeButton("ยกเลิก",null).setPositiveButton("ล้างชุด"){_,_->if(sessionId.isNotEmpty())db.cancelSession(sessionId);resetAll()}.show()}
    private fun resetAll(){stage=Stage.EMPLOYEE;sessionId="";sequence=0;retryCount=0;employeeName="";employeeRaw="";comparePick=true;pickRaw="";pickMatch=null;kanbanRaw="";kanbanPart="";standPart="";workQty=0;expectedBoxes=0;boxes.clear();pendingError=false;overrideReason="";rawEvents.clear();whitePanel();status.text="รอ Scan พนักงาน";stepView.text="SCAN EMPLOYEE";instruction.text="ตอนนี้: Scan QR พนักงาน • ถัดไป: Scan Pick List";updateFlowBar();employeeView.text="ผู้ตรวจ: —";standView.text="STAND\n—";kanbanView.text="KANBAN\n—";boxView.text="BOX TAG\n0";difference.text="";listOf(rawButton,boxDoneButton,resetBatchButton,rescanButton,nextButton,clearButton).forEach{it.visibility=View.GONE};clearButton.isEnabled=false;focusScanner()}
    private fun showRaw()=AlertDialog.Builder(this).setTitle("RAW DATA").setMessage(rawEvents.joinToString("\n\n").ifBlank{"—"}).setPositiveButton("ปิด",null).show()
    private fun showHistory(){val items=db.history();if(items.isEmpty())return;val labels=items.map{"${Date(it.startedAt)} ${it.result}\n${it.employeeName} • ${it.partNo}"}.toTypedArray();AlertDialog.Builder(this).setTitle("ประวัติ").setItems(labels){_,i->AlertDialog.Builder(this).setTitle("รายละเอียด").setMessage(db.historyDetail(items[i].sessionId)).setPositiveButton("ปิด",null).show()}.setNegativeButton("ปิด",null).show()}
    private fun exportAndShare(){
        if(stage==Stage.DASHBOARD&&db.syncStatus(sessionId)!="SYNCED"){
            AlertDialog.Builder(this).setTitle("ข้อมูลกลางยังรอส่ง").setMessage("แอปเก็บคิวไว้แล้วและจะส่งเมื่อมีอินเทอร์เน็ต\n\nต้องการเปิด Outlook ต่อหรือไม่?").setNegativeButton("รอให้ Sync สำเร็จ",null).setPositiveButton("เปิด Outlook"){_,_->shareCsv()}.show()
            return
        }
        shareCsv()
    }
    private fun shareCsv(){try{val ts=SimpleDateFormat("yyyy-MM-dd_HHmmss",Locale.US).format(Date());val file=db.exportCsv(File(cacheDir,"exports/CheckTag_RS_$ts.csv"));val uri=FileProvider.getUriForFile(this,"$packageName.files",file);val send=Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_EMAIL,arrayOf("wirachai.so@tskforging.com","sart.ka@tskforging.com","arnon.ju@tskforging.com"));putExtra(Intent.EXTRA_STREAM,uri);putExtra(Intent.EXTRA_SUBJECT,"Check Tag_RS $kanbanPart ${if(overrideReason.isBlank())"OK" else "WARNING"}");putExtra(Intent.EXTRA_TEXT,"ผู้ตรวจ: $employeeName\nจำนวนงาน: $workQty PCS\nBOX: กำหนด $expectedBoxes / Scan ${boxes.size}\n${boxDifference()}\n${if(overrideReason.isNotBlank())"เหตุผล: $overrideReason" else ""}");clipData=ClipData.newRawUri(file.name,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};try{startActivity(Intent(send).apply{setPackage("com.microsoft.office.outlook")})}catch(_:Exception){startActivity(Intent.createChooser(send,"เลือกแอป Mail"))}}catch(e:Exception){Toast.makeText(this,"ส่งออกไม่สำเร็จ: ${e.message}",Toast.LENGTH_LONG).show()}}
    private fun enqueueCentralSync(){
        val request=OneTimeWorkRequestBuilder<CentralSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(this).enqueueUniqueWork("check_tag_rs_central_sync",ExistingWorkPolicy.REPLACE,request)
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id).observe(this){if(stage==Stage.DASHBOARD&&sessionId.isNotBlank()){showDashboard(if(overrideReason.isBlank())"OK" else "WARNING")}}
    }
    private fun syncLabel(value:String)=when(value){"SYNCED"->"ส่งสำเร็จ ✓";"SENDING"->"กำลังส่ง…";"PENDING"->"รออินเทอร์เน็ต/รอส่ง";else->value}
    private fun whitePanel(){panel.setBackgroundColor(Color.WHITE);listOf(employeeView,standView,boxView,kanbanView,difference).forEach{it.setTextColor(Color.rgb(16,24,40))};status.setTextColor(Color.rgb(52,64,84))}
    private fun focusScanner(){input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)}
    private fun sha256(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
}
