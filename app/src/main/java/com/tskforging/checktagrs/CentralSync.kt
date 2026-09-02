package com.tskforging.checktagrs

import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class CentralSession(val id:String,val startedAt:Long,val completedAt:Long,val employee:String,val result:String,val pickMode:String,val pickJcc:String?,val kanbanJcc:String?,val partNo:String?,val workQty:Int,val expectedBoxes:Int,val actualBoxes:Int,val reason:String?,val standPart:String?,val boxParts:String?,val retryCount:Int,val appVersion:String,val deviceId:String)
data class CentralScanEvent(val id:String,val sequence:Int,val scannedAt:Long,val target:String,val raw:String,val sha256:String,val tagType:String,val partNo:String?,val ruleId:String,val ruleVersion:String,val parseResult:String,val compareResult:String)

object CentralPayloadJson {
    fun escape(v:String?)=(v?:"").replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")
    private fun q(v:String?)="\"${escape(v)}\""
    fun encode(s:CentralSession,events:List<CentralScanEvent>):String {
        val session="""{"session_id":${q(s.id)},"started_at":${q(Instant.ofEpochMilli(s.startedAt).toString())},"completed_at":${q(Instant.ofEpochMilli(s.completedAt).toString())},"employee_name":${q(s.employee)},"result":${q(s.result)},"customer":${q(if(s.pickMode=="COMPARE")"AISIN" else "")},"pick_list_mode":${q(s.pickMode)},"pick_list_jcc":${q(s.pickJcc)},"kanban_jcc":${q(s.kanbanJcc)},"part_no":${q(s.partNo)},"work_qty":${s.workQty},"expected_boxes":${s.expectedBoxes},"actual_boxes":${s.actualBoxes},"box_difference":${s.actualBoxes-s.expectedBoxes},"override_reason":${q(s.reason)},"stand_part":${q(s.standPart)},"box_parts":${q(s.boxParts)},"retry_count":${s.retryCount},"app_version":${q(s.appVersion)},"device_id":${q(s.deviceId)}}"""
        val eventJson=events.joinToString(","){e->"""{"event_id":${q(e.id)},"scan_sequence":${e.sequence},"scanned_at":${q(Instant.ofEpochMilli(e.scannedAt).toString())},"scan_target":${q(e.target)},"raw_data_full":${q(e.raw)},"raw_sha256":${q(e.sha256)},"tag_type":${q(e.tagType)},"extracted_part_no":${q(e.partNo)},"parser_rule_id":${q(e.ruleId)},"parser_rule_version":${q(e.ruleVersion)},"parse_result":${q(if(e.parseResult=="SUCCESS")"OK" else "ERROR")},"compare_result":${q(if(e.compareResult in listOf("MATCH","MISMATCH","NOT_APPLICABLE"))e.compareResult else "NOT_APPLICABLE")}}"""}
        return "{\"session\":$session,\"scan_events\":[$eventJson]}"
    }
    fun accepted(response:String)=Regex("\\\"ok\\\"\\s*:\\s*true",RegexOption.IGNORE_CASE).containsMatchIn(response)
}

object CentralApi {
    const val ENDPOINT="https://script.google.com/macros/s/AKfycbxS-jaE6QBM_JE3UbuNRk3ighMvtPsUTGyeleMYkM4oUAK0Kh05yS6EU7kDOtqUn_3Ziw/exec"
    fun post(payload:String):Result<String> = runCatching {
        val connection=(URL(ENDPOINT).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=15000;readTimeout=20000;doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8");setRequestProperty("Accept","application/json")}
        connection.outputStream.use{it.write(payload.toByteArray(Charsets.UTF_8))}
        val code=connection.responseCode
        val body=(if(code in 200..299)connection.inputStream else connection.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
        connection.disconnect()
        if(code !in 200..299)error("HTTP $code")
        if(!CentralPayloadJson.accepted(body))error("API rejected data")
        body
    }
}
