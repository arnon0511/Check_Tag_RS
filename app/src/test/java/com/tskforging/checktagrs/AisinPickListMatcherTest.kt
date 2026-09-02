package com.tskforging.checktagrs

import org.junit.Assert.*
import org.junit.Test

class AisinPickListMatcherTest {
    private val pick="JCC60902004200007971374             41J631   JC7D42   01                    2026/09/0223:00"
    private val kanban="XXXXXXX 3J631 JC21JC 7D42 0116171-05030 J631 0100000752RE28-11 B014 0000040000PCS 20260902-00009 JCC60902004200 000000000400000"

    @Test fun confirmedAisinPairMatchesUsingThreeKeys() {
        val result=AisinPickListMatcher.compare(pick,kanban)
        assertTrue(result.success)
        assertEquals("JCC60902004200007971374",result.pickJcc)
        assertEquals("JCC60902004200",result.kanbanJcc)
        assertEquals("J631",result.groupCode)
        assertEquals("7D42",result.routeCode)
    }

    @Test fun rejectsWrongJccGroupRouteAndMissingFields() {
        assertFalse(AisinPickListMatcher.compare(pick,kanban.replace("JCC60902004200","JCC60902009999")).success)
        assertFalse(AisinPickListMatcher.compare(pick.replace("J631","J999"),kanban).success)
        assertFalse(AisinPickListMatcher.compare(pick.replace("7D42","8A99"),kanban).success)
        assertFalse(AisinPickListMatcher.compare("NO DATA",kanban).success)
    }

    @Test fun whitespaceAndCaseDoNotAffectDocumentMatch() {
        assertTrue(AisinPickListMatcher.compare(pick.lowercase().replace(' ','\u00a0'),kanban).success)
    }
}
