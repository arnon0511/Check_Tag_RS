package com.tskforging.checktagrs

import org.junit.Assert.*
import org.junit.Test

class MultiKanbanFlowTest {
    @Test fun productionFlowScenarios() { assertEquals(18, MultiKanbanFlowChecks.runAll()) }

    @Test fun confirmedDnthInputRunsThroughRealParsersAndThreePairs() {
        val disc = "DISC5060020000010101000210125104151120710725124061290515207154081550911TG028382-502C            TG028993-590A 0000040                         C07        3001660                 T-5          6082501901TG028382-502C       01"
        val flow = MultiKanbanFlow(true, TagParser::partsMatch)
        assertTrue(flow.accept(TagParser.stand("TG028993-590 A").partNo))
        repeat(3) { assertTrue(flow.accept(TagParser.kanban(disc).partNo)) }
        assertEquals(MultiKanbanFlow.Target.KANBAN, flow.target)
        assertTrue(flow.finishKanbans())
        for (raw in listOf("TG028993-590 A", "ITG028993-590A", "PD26080101|FP01|PART|TG028993-590A|40|PCS")) {
            assertTrue(flow.accept(TagParser.box(raw).partNo))
        }
        assertEquals(0, flow.remaining)
        assertTrue(flow.finish())
    }

    @Test fun wrongDocumentAtKanbanStepDoesNotCountAsKanban() {
        val flow = MultiKanbanFlow(false, TagParser::partsMatch)
        assertFalse(flow.accept(TagParser.kanban("TG028993-590 A").partNo))
        assertTrue(flow.kanbans.isEmpty())
        assertFalse(flow.finishKanbans())
    }

    @Test fun existingJtektComparisonAndDifferentLotsRemainSupported() {
        val flow = MultiKanbanFlow(true, TagParser::partsMatch)
        assertTrue(flow.accept(TagParser.stand("|JGC123456-40").partNo))
        repeat(2) { assertTrue(flow.accept(TagParser.kanban("KANBAN JGC123456-99").partNo)) }
        assertTrue(flow.finishKanbans())
        for (lot in listOf("LOT1", "LOT2")) {
            assertTrue(flow.accept(TagParser.box("PD26080101|FP01|PART|JGC123456-31-2|PART|40|PCS|10|KGS|TSK2|$lot").partNo))
        }
        assertTrue(flow.finish())
    }
}
