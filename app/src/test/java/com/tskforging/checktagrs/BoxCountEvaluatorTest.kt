package com.tskforging.checktagrs

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxCountEvaluatorTest {
    @Test fun identifiesUnderMatchAndOverImmediately() {
        assertEquals(BoxCountStatus.UNDER,BoxCountEvaluator.status(10,9))
        assertEquals(BoxCountStatus.MATCH,BoxCountEvaluator.status(10,10))
        assertEquals(BoxCountStatus.OVER,BoxCountEvaluator.status(10,11))
    }
}
