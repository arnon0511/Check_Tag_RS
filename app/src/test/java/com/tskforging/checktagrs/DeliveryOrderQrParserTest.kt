package com.tskforging.checktagrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryOrderQrParserTest {
    @Test fun confirmedDeliveryOrderQrIsParsed() {
        val result = DeliveryOrderQrParser.parse("CHECKTAGRS|DO|PART=TG053661-7151|QTY=1100|BOX=11")
        assertEquals("TG053661-7151", result?.partNo)
        assertEquals(1100, result?.currentQty)
        assertEquals(11, result?.numberOfBoxes)
    }

    @Test fun whitespaceAndCaseAreAccepted() {
        val result = DeliveryOrderQrParser.parse("checktagrs|do|part= tg067126-2030 |qty=2,000|box=1")
        assertEquals(2000, result?.currentQty)
        assertEquals(1, result?.numberOfBoxes)
    }

    @Test fun zeroQuantityIsRejected() {
        assertNull(DeliveryOrderQrParser.parse("CHECKTAGRS|DO|PART=TG067122-1921|QTY=0|BOX=4"))
    }

    @Test fun incompleteQrIsRejected() {
        assertNull(DeliveryOrderQrParser.parse("CHECKTAGRS|DO|QTY=1100|BOX=11"))
    }
}
