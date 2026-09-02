package com.tskforging.checktagrs

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EvidenceDb(context: Context) : SQLiteOpenHelper(context, "check_tag_rs.db", null, 6) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE sessions(session_id TEXT PRIMARY KEY,started_at INTEGER NOT NULL,completed_at INTEGER,final_result TEXT NOT NULL DEFAULT 'IN_PROGRESS',retry_count INTEGER NOT NULL DEFAULT 0,app_version TEXT NOT NULL,stand_part TEXT,box_part TEXT,kanban_part TEXT,stand_check_mode TEXT NOT NULL DEFAULT 'CHECK',employee_name TEXT NOT NULL DEFAULT '',employee_raw TEXT NOT NULL DEFAULT '')""")
        db.execSQL("""CREATE TABLE scan_events(event_id TEXT PRIMARY KEY,session_id TEXT NOT NULL,scan_sequence INTEGER NOT NULL,scanned_at INTEGER NOT NULL,scan_target TEXT NOT NULL,raw_data_full TEXT NOT NULL,raw_length INTEGER NOT NULL,raw_sha256 TEXT NOT NULL,detected_tag_type TEXT NOT NULL,extracted_part_no TEXT,parser_rule_id TEXT NOT NULL,parser_rule_version TEXT NOT NULL,parse_result TEXT NOT NULL,compare_result TEXT NOT NULL,rescan_of_event_id TEXT)""")
        addInspectionColumns(db)
        addSyncColumns(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN stand_part TEXT")
            db.execSQL("ALTER TABLE sessions ADD COLUMN box_part TEXT")
            db.execSQL("ALTER TABLE sessions ADD COLUMN kanban_part TEXT")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE sessions ADD COLUMN stand_check_mode TEXT NOT NULL DEFAULT 'CHECK'")
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN employee_name TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE sessions ADD COLUMN employee_raw TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 5) addInspectionColumns(db)
        if (oldVersion < 6) addSyncColumns(db)
    }

    private fun addInspectionColumns(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN pick_list_mode TEXT NOT NULL DEFAULT 'SKIP'")
        db.execSQL("ALTER TABLE sessions ADD COLUMN pick_list_raw TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sessions ADD COLUMN pick_list_jcc TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sessions ADD COLUMN kanban_jcc TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sessions ADD COLUMN work_qty INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sessions ADD COLUMN expected_boxes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sessions ADD COLUMN actual_boxes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sessions ADD COLUMN override_reason TEXT NOT NULL DEFAULT ''")
    }

    private fun addSyncColumns(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'NOT_READY'")
        db.execSQL("ALTER TABLE sessions ADD COLUMN sync_attempts INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sessions ADD COLUMN sync_last_error TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sessions ADD COLUMN sync_last_at INTEGER")
    }

    fun startSession(id: String, checkStand: Boolean, employeeName: String, employeeRaw: String) = writableDatabase.insertOrThrow("sessions", null, ContentValues().apply {
        put("session_id", id); put("started_at", System.currentTimeMillis()); put("app_version", "0.18.1")
        put("stand_check_mode", if(checkStand) "CHECK" else "SKIP")
        put("employee_name", employeeName); put("employee_raw", employeeRaw)
    })

    fun saveInspectionDetails(id:String, comparePick:Boolean, pickRaw:String, pickJcc:String?, kanbanJcc:String?, workQty:Int, expectedBoxes:Int) =
        writableDatabase.update("sessions", ContentValues().apply {
            put("pick_list_mode",if(comparePick)"CHECK" else "SKIP"); put("pick_list_raw",pickRaw)
            put("pick_list_jcc",pickJcc ?: ""); put("kanban_jcc",kanbanJcc ?: "")
            put("work_qty",workQty); put("expected_boxes",expectedBoxes)
        },"session_id=?",arrayOf(id))

    fun saveEvent(e: ScanEvidence) = writableDatabase.insertOrThrow("scan_events", null, ContentValues().apply {
        put("event_id", e.eventId); put("session_id", e.sessionId); put("scan_sequence", e.sequence)
        put("scanned_at", e.scannedAt); put("scan_target", e.target.name); put("raw_data_full", e.raw)
        put("raw_length", e.raw.length); put("raw_sha256", e.sha256); put("detected_tag_type", e.tagType)
        put("extracted_part_no", e.partNo); put("parser_rule_id", e.ruleId); put("parser_rule_version", e.ruleVersion)
        put("parse_result", e.parseResult); put("compare_result", e.compareResult); put("rescan_of_event_id", e.rescanOf)
    })

    fun finishSession(id: String, result: String, retries: Int, parts: Map<ScanTarget, String>, actualBoxes:Int=0, overrideReason:String="") =
        writableDatabase.update("sessions", ContentValues().apply {
            put("completed_at", System.currentTimeMillis()); put("final_result", result); put("retry_count", retries)
            put("stand_part", parts[ScanTarget.STAND]); put("box_part", parts[ScanTarget.BOX_TAG]); put("kanban_part", parts[ScanTarget.KANBAN])
            put("actual_boxes",actualBoxes); put("override_reason",overrideReason)
            put("sync_status","PENDING"); put("sync_last_error","")
        }, "session_id=?", arrayOf(id))

    fun pendingSyncIds(): List<String> {
        val ids=mutableListOf<String>()
        readableDatabase.rawQuery("SELECT session_id FROM sessions WHERE final_result IN ('OK','WARNING') AND sync_status IN ('PENDING','SENDING','ERROR') ORDER BY completed_at",null).use{c->while(c.moveToNext())ids+=c.getString(0)}
        return ids
    }

    fun syncStatus(id:String):String = readableDatabase.rawQuery("SELECT sync_status FROM sessions WHERE session_id=?",arrayOf(id)).use{c->if(c.moveToFirst())c.getString(0) else "NOT_FOUND"}

    fun markSyncing(id:String)=writableDatabase.update("sessions",ContentValues().apply{put("sync_status","SENDING");put("sync_attempts",1+syncAttempts(id));put("sync_last_at",System.currentTimeMillis())},"session_id=?",arrayOf(id))
    fun markSynced(id:String)=writableDatabase.update("sessions",ContentValues().apply{put("sync_status","SYNCED");put("sync_last_error","");put("sync_last_at",System.currentTimeMillis())},"session_id=?",arrayOf(id))
    fun markPending(id:String,error:String)=writableDatabase.update("sessions",ContentValues().apply{put("sync_status","PENDING");put("sync_last_error",error.take(500));put("sync_last_at",System.currentTimeMillis())},"session_id=?",arrayOf(id))
    private fun syncAttempts(id:String)=readableDatabase.rawQuery("SELECT sync_attempts FROM sessions WHERE session_id=?",arrayOf(id)).use{c->if(c.moveToFirst())c.getInt(0) else 0}

    fun buildSyncPayload(id:String,deviceId:String):String {
        val session=readableDatabase.rawQuery("SELECT started_at,completed_at,employee_name,final_result,pick_list_mode,pick_list_jcc,kanban_jcc,kanban_part,work_qty,expected_boxes,actual_boxes,override_reason,stand_part,box_part,retry_count,app_version FROM sessions WHERE session_id=?",arrayOf(id)).use{c->
            require(c.moveToFirst()){ "Session not found" }
            CentralSession(id,c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),if(c.getString(4)=="CHECK")"COMPARE" else "SKIP",c.getString(5),c.getString(6),c.getString(7),c.getInt(8),c.getInt(9),c.getInt(10),c.getString(11),c.getString(12),c.getString(13),c.getInt(14),c.getString(15),deviceId)
        }
        val events=mutableListOf<CentralScanEvent>()
        readableDatabase.rawQuery("SELECT event_id,scan_sequence,scanned_at,scan_target,raw_data_full,raw_sha256,detected_tag_type,extracted_part_no,parser_rule_id,parser_rule_version,parse_result,compare_result FROM scan_events WHERE session_id=? ORDER BY scan_sequence",arrayOf(id)).use{c->while(c.moveToNext())events+=CentralScanEvent(c.getString(0),c.getInt(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),c.getString(10),c.getString(11))}
        return CentralPayloadJson.encode(session,events)
    }

    fun cancelSession(id: String) = writableDatabase.update("sessions", ContentValues().apply {
        put("completed_at", System.currentTimeMillis()); put("final_result", "CANCELLED")
    }, "session_id=? AND final_result='IN_PROGRESS'", arrayOf(id))

    fun history(): List<HistoryItem> {
        val out = mutableListOf<HistoryItem>()
        readableDatabase.rawQuery("SELECT session_id,started_at,final_result,employee_name,COALESCE(stand_part,box_part,'—'),stand_check_mode FROM sessions WHERE final_result NOT IN ('IN_PROGRESS','CANCELLED') ORDER BY started_at DESC LIMIT 100", null).use { c ->
            while (c.moveToNext()) out += HistoryItem(c.getString(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5))
        }
        return out
    }

    fun historyDetail(sessionId: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val lines = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT started_at,completed_at,final_result,employee_name,employee_raw,stand_check_mode,stand_part,box_part,kanban_part,retry_count,pick_list_mode,pick_list_jcc,kanban_jcc,work_qty,expected_boxes,actual_boxes,override_reason FROM sessions WHERE session_id=?", arrayOf(sessionId)).use { c ->
            if (c.moveToFirst()) {
                lines += "วันที่–เวลา: ${fmt.format(Date(c.getLong(0)))}"
                lines += "ผู้ตรวจ: ${c.getString(3).ifBlank { "—" }}"
                lines += "QR พนักงาน: ${c.getString(4).ifBlank { "—" }}"
                lines += "ผลตรวจ: ${c.getString(2)}"
                lines += "STAND: ${if(c.getString(5)=="SKIP") "ข้ามการตรวจ" else c.getString(6) ?: "—"}"
                lines += "BOX TAG: ${c.getString(7) ?: "—"}"
                lines += "KANBAN: ${c.getString(8) ?: "—"}"
                lines += "สแกนซ้ำ: ${c.getInt(9)} ครั้ง"
                lines += "Pick List: ${if(c.getString(10)=="SKIP") "ข้ามการตรวจ" else c.getString(11).ifBlank{"—"}}"
                lines += "JCC KANBAN: ${c.getString(12).ifBlank{"—"}}"
                lines += "จำนวนงาน: ${c.getInt(13)} PCS"
                lines += "จำนวน Box: กำหนด ${c.getInt(14)} / Scan ${c.getInt(15)}"
                if(c.getString(16).isNotBlank()) lines += "เหตุผลยืนยันส่ง: ${c.getString(16)}"
            }
        }
        lines += "\nข้อมูลดิบตามลำดับการสแกน"
        readableDatabase.rawQuery("SELECT scan_sequence,scan_target,raw_data_full,extracted_part_no,detected_tag_type,parser_rule_id,parse_result,compare_result,scanned_at FROM scan_events WHERE session_id=? ORDER BY scan_sequence", arrayOf(sessionId)).use { c ->
            while (c.moveToNext()) {
                lines += "\n#${c.getInt(0)} ${c.getString(1)}  ${fmt.format(Date(c.getLong(8)))}\nRAW: ${c.getString(2)}\nPart: ${c.getString(3) ?: "—"}\nType/Rule: ${c.getString(4)} / ${c.getString(5)}\nParse/Compare: ${c.getString(6)} / ${c.getString(7)}"
            }
        }
        lines += "\nSession ID: $sessionId"
        return lines.joinToString("\n")
    }

    fun exportCsv(target: File): File {
        target.parentFile?.mkdirs()
        target.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("\uFEFFdate_time,employee_name,employee_qr_raw,result,pick_list_mode,pick_list_jcc,kanban_jcc,work_qty,expected_boxes,actual_boxes,box_difference,override_reason,stand_check_mode,stand_part,box_part,kanban_part,retry_count,raw_scan_history,session_id\n")
            readableDatabase.rawQuery("SELECT started_at,employee_name,employee_raw,final_result,pick_list_mode,pick_list_jcc,kanban_jcc,work_qty,expected_boxes,actual_boxes,override_reason,stand_check_mode,stand_part,box_part,kanban_part,retry_count,session_id FROM sessions WHERE final_result NOT IN ('IN_PROGRESS','CANCELLED') ORDER BY started_at DESC", null).use { c ->
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                while (c.moveToNext()) {
                    val sessionId = c.getString(16)
                    val rawHistory = mutableListOf<String>()
                    readableDatabase.rawQuery("SELECT scan_sequence,scan_target,raw_data_full,extracted_part_no,parse_result,compare_result FROM scan_events WHERE session_id=? ORDER BY scan_sequence", arrayOf(sessionId)).use { e ->
                        while(e.moveToNext()) rawHistory += "#${e.getInt(0)} ${e.getString(1)} | RAW=${e.getString(2)} | PART=${e.getString(3) ?: ""} | ${e.getString(4)}/${e.getString(5)}"
                    }
                    val values = listOf(fmt.format(Date(c.getLong(0))),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getInt(7).toString(),c.getInt(8).toString(),c.getInt(9).toString(),(c.getInt(9)-c.getInt(8)).toString(),c.getString(10),c.getString(11),c.getString(12),c.getString(13),c.getString(14),c.getInt(15).toString(),rawHistory.joinToString("\n"),sessionId)
                    w.write(values.joinToString(",") { csv(it ?: "") }); w.newLine()
                }
            }
        }
        return target
    }

    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
