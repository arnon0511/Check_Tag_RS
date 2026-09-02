package com.tskforging.checktagrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CentralPayloadJsonTest {
    private val session=CentralSession("session-1",0,1000,"Mr.Burin","OK","COMPARE","JCC60902004200","JCC60902004200","16171-05030",40,1,1,"","16171-05030","16171-05030",0,"0.18.1","PM75-A")

    @Test fun payloadContainsSessionEventsAndEscapesRawData(){
        val event=CentralScanEvent("event-1",1,0,"KANBAN","A\"B\nC","abc","AISIN_KANBAN","16171-05030","aisin","1.0","SUCCESS","MATCH")
        val json=CentralPayloadJson.encode(session,listOf(event))
        assertTrue(json.contains("\"session_id\":\"session-1\""))
        assertTrue(json.contains("\"raw_data_full\":\"A\\\"B\\nC\""))
        assertTrue(json.contains("\"parse_result\":\"OK\""))
        assertTrue(json.contains("\"scan_events\":["))
    }

    @Test fun apiResponseRequiresExplicitOkTrue(){
        assertTrue(CentralPayloadJson.accepted("{\"ok\": true, \"duplicate\":true}"))
        assertFalse(CentralPayloadJson.accepted("{\"ok\":false}"))
        assertFalse(CentralPayloadJson.accepted("not json"))
    }
}
