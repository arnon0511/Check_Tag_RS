package com.tskforging.checktagrs

import org.junit.Assert.*
import org.junit.Test

class SingleKanbanBatchFlowTest {
    private val disc = "DISC5060020000010101000210125104151120710725124061290515207154081550911TG028382-502C            TG028993-590A 0000040                         C07        3001660                 T-5          6082501901TG028382-502C       01"

    @Test fun productionFlowScenarios() { assertEquals(16, SingleKanbanBatchFlowChecks.runAll()) }

    @Test fun oneDnthKanbanChecksManyBoxesUsingExistingParsers() {
        val flow = SingleKanbanBatchFlow(true, TagParser::partsMatch)
        assertTrue(flow.accept(TagParser.stand("TG028993-590 A").partNo))
        assertTrue(flow.accept(TagParser.kanban(disc).partNo))
        assertEquals(SingleKanbanBatchFlow.Target.BOX_TAG, flow.target)
        for (raw in listOf("TG028993-590 A", "ITG028993-590A", "PD26080101|FP01|PART|TG028993-590A|40|PCS")) {
            assertTrue(flow.accept(TagParser.box(raw).partNo))
            assertEquals("TG028993-590A", flow.kanbanPart)
        }
        assertEquals(3, flow.boxes.size)
        assertTrue(flow.finish())
    }

    @Test fun secondKanbanScannedAtBoxStepIsRejectedWithoutReplacingReference() {
        val flow = SingleKanbanBatchFlow(false, TagParser::partsMatch)
        assertTrue(flow.accept(TagParser.kanban(disc).partNo))
        assertFalse(flow.accept(TagParser.box(disc).partNo))
        assertEquals("TG028993-590A", flow.kanbanPart)
        assertTrue(flow.boxes.isEmpty())
        assertTrue(flow.accept(TagParser.box("TG028993-590 A").partNo))
        assertTrue(flow.finish())
    }

    @Test fun wrongDnthSuffixDoesNotPass() {
        val flow = SingleKanbanBatchFlow(true, TagParser::partsMatch)
        assertTrue(flow.accept(TagParser.stand("TG028993-590 A").partNo))
        assertTrue(flow.accept(TagParser.kanban(disc).partNo))
        assertFalse(flow.accept(TagParser.box("TG028993-590B").partNo))
        assertFalse(flow.finish())
        assertTrue(flow.accept(TagParser.box("TG028993-590 A").partNo))
        assertTrue(flow.finish())
    }

    @Test fun jtektAndDifferentLotsRemainSupported() {
        val flow = SingleKanbanBatchFlow(true, TagParser::partsMatch)
        assertTrue(flow.accept(TagParser.stand("|JGC123456-40").partNo))
        assertTrue(flow.accept(TagParser.kanban("KANBAN JGC123456-99").partNo))
        for (lot in listOf("LOT1", "LOT2")) {
            assertTrue(flow.accept(TagParser.box("PD26080101|FP01|PART|JGC123456-31-2|PART|40|PCS|10|KGS|TSK2|$lot").partNo))
        }
        assertTrue(flow.finish())
    }
}
