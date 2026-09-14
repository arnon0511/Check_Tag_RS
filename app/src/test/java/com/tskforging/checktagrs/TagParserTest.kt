package com.tskforging.checktagrs

import org.junit.Assert.*
import org.junit.Test

class TagParserTest {
    @Test
    fun snssKanbanReadsFirstFieldAsPartNumber() {
        val result = TagParser.kanban("7521T0376    260805    80\nCLM012")

        assertTrue(result.success)
        assertEquals("7521T0376", result.partNo)
        assertEquals("KANBAN_SNSS", result.tagType)
    }

    @Test fun parsesConfirmedAisinSample() {
        val raw = "XXXXXXX   3J631   JC21JC 7D42   01213161-17170            J631   0100000753RE02-22"
        val result = TagParser.kanban(raw)
        assertTrue(result.success)
        assertEquals("1213161-17170", result.partNo)
        assertEquals("KANBAN_AISIN", result.tagType)
    }

    @Test fun parsesAisinWithoutJoiningItsPrefixToPreviousField() {
        val result = TagParser.kanban("7D42   0 1213161 - 17170   J631")

        assertTrue(result.success)
        assertEquals("1213161-17170", result.partNo)
        assertEquals("KANBAN_AISIN", result.tagType)
    }

    @Test fun parsesConfirmedDnthSampleAndRequiresMatchingCopies() {
        val raw = "DISC5060020000010101000210125104151120710725124061290515207154081550911 TGY94159-0010 0000012 C07 3163955 T-2 60805369 TGY94159-0010 01"
        val result = TagParser.kanban(raw)
        assertTrue(result.success)
        assertEquals("TGY94159-0010", result.partNo)
        assertEquals("KANBAN_DNTH", result.tagType)
    }

    @Test fun rejectsConflictingDnthCopies() {
        val result = TagParser.kanban("TGY94159-0010 data TGY94159-0011")
        assertFalse(result.success)
    }

    @Test fun parsesDnthTgFormatAndMatchesBoxAfterRemovingIPrefix() {
        val raw = "DISC5060020000010101000210 125104151120710725124061290515207154081550911 TG028351-5130 0000120 C07 3003581 T-3 60805047 TG028351-5130 01"
        val box = TagParser.box("ITG028351-5130")
        val kanban = TagParser.kanban(raw)

        assertTrue(box.success)
        assertTrue(kanban.success)
        assertEquals("TG028351-5130", box.partNo)
        assertEquals("TG028351-5130", kanban.partNo)
        assertEquals(box.partNo, kanban.partNo)
        assertEquals("KANBAN_DNTH", kanban.tagType)
    }

    @Test fun jathBoxVariantIsWarningInsteadOfSilentMatch() {
        val box = TagParser.box("PD26080501|FP0001|PART|JGF02-002060-31-4")
        val kanban = TagParser.kanban("JGF02-002060-31")

        assertTrue(box.success)
        assertTrue(kanban.success)
        assertEquals("KANBAN_JATH", kanban.tagType)
        assertFalse(TagParser.partsMatch(box.partNo!!, kanban.partNo!!))
        assertEquals(PartComparison.WARNING, TagParser.compareParts(box.partNo!!, kanban.partNo!!).result)
        assertEquals("JGF02-002060-31-4", TagParser.comparisonPart(box.partNo!!))
    }

    @Test fun jathWithoutBoxVariantAlsoMatches() {
        val box = TagParser.box("PD26080501|FP0001|PART|JGF02-002060-31")
        val kanban = TagParser.kanban("JGF02-002060-31")

        assertTrue(box.success)
        assertTrue(kanban.success)
        assertTrue(TagParser.partsMatch(box.partNo!!, kanban.partNo!!))
    }

    @Test fun jathRuleDoesNotStripDnthSuffix() {
        assertEquals("TG028351-5130", TagParser.comparisonPart("TG028351-5130"))
        assertFalse(TagParser.partsMatch("TG028351-5130", "TG028351"))
    }

    @Test fun jtektDifferentSuffixesRequireWarningForAllTagTypes() {
        val stand = TagParser.stand("|JGC123456-40")
        val box = TagParser.box("PD26080501|FP0001|PART|JGC123456-31-2")
        val kanban = TagParser.kanban("KANBAN JGC123456-99")

        assertTrue(stand.success)
        assertTrue(box.success)
        assertTrue(kanban.success)
        assertEquals("JGC123456-40", TagParser.comparisonPart(stand.partNo!!))
        assertEquals(PartComparison.WARNING, TagParser.compareParts(stand.partNo!!, box.partNo!!).result)
        assertEquals(PartComparison.WARNING, TagParser.compareParts(box.partNo!!, kanban.partNo!!).result)
        assertFalse(TagParser.partsMatch(stand.partNo!!, box.partNo!!))
    }

    @Test fun noHyphenVersusSuffixedTagIsMismatch() {
        assertEquals(PartComparison.MISMATCH, TagParser.compareParts("JGC123456", "JGC123456-40").result)
    }

    @Test fun rejectsUnknownKanbanInsteadOfGuessing() {
        assertFalse(TagParser.kanban("UNKNOWN CUSTOMER DATA").success)
    }

    @Test fun normalizesSpaceInStandAndNewAisinPrefix() {
        assertEquals("16171-05030", TagParser.stand("|16171- 05030").partNo)
        assertEquals("16171-05030", TagParser.kanban("01 16171-05030").partNo)
    }
    private val discSample = "DISC5060020000010101000210125104151120710725124061290515207154081550911TG028382-502C            TG028993-590A 0000040                         C07        3001660                 T-5          6082501901TG028382-502C       01"

    @Test fun discSelectsBoxFieldNotFirstOrRepeatedCustomerPart() {
        val result = TagParser.kanban(discSample)
        assertTrue(result.success)
        assertEquals("TG028993-590A", result.partNo)
        assertEquals("dnth_disc_box_tg_primary", result.ruleId)
        assertTrue(TagParser.partsMatch(TagParser.stand("|TG028993-590 A").partNo!!, result.partNo!!))
        assertTrue(TagParser.partsMatch(TagParser.box("|TG028993-590 A").partNo!!, result.partNo!!))
        assertFalse(TagParser.partsMatch("TG028382-502C", result.partNo!!))
        assertEquals(PartComparison.WARNING, TagParser.compareParts("TG028993-590B", result.partNo!!).result)
    }

    @Test fun discHandlesUnicodePaddingAndWhitespaceInsidePart() {
        val raw = discSample.replace("TG028993-590A", "TG028993-590 A")
            .replace(' ', '\u00a0') + "\r\n"
        assertEquals("TG028993-590A", TagParser.kanban(raw).partNo)
        assertTrue(TagParser.partsMatch(" TG028993-590\u00a0A\u202f", "TG028993-590A"))
        assertEquals("TG028993-590A", TagParser.box("ITG028993-590 A").partNo)
    }

    @Test fun discRejectsConflictingReferenceEvenWhenBoxMatches() {
        val raw = discSample.replace("6082501901TG028382-502C", "6082501901TG028382-503C")
        assertFalse(TagParser.kanban(raw).success)
    }

    @Test fun discAllowsMissingMetadataButRejectsMissingPartAndExtraCandidate() {
        assertTrue(TagParser.kanban(discSample.replace("0000040", "")).success)
        assertTrue(TagParser.kanban(discSample.replace("C07        3001660                 T-5          6082501901", "")).success)
        assertFalse(TagParser.kanban(discSample.replace("TG028993-590A", "BAD-PART")).success)
        assertFalse(TagParser.kanban(discSample.replace("0000040", "TG028993-591A 0000040")).success)
        assertFalse(TagParser.kanban(discSample.replace("TG028993-590A", "TG028993-590A_")).success)
    }

    @Test fun discAllowsBlankOptionalPartAndLaneInConfirmedStructure() {
        val raw = discSample.replace("TG028993-590A", "").replace("T-5", "")
        assertEquals("TG028382-502C", TagParser.kanban(raw).partNo)
    }

    @Test fun legacyDnthStillRejectsDifferentSuffixes() {
        assertFalse(TagParser.kanban("TG028351-5130 data TG028351-5131").success)
        assertEquals(PartComparison.WARNING, TagParser.compareParts("JGC123456-40", "JGC123456-31-2").result)
        assertEquals(PartComparison.WARNING, TagParser.compareParts("TG028382-502C", "TG028382-503C").result)
    }
    @Test fun readsPlainDnthStandAndBoxWithAsciiAndUnicodeSpaces() {
        for (raw in listOf("TG028993-590 A", " TG028993-590\u00a0A\r\n", "tg028993-590a", "TG028993-590\u202fA")) {
            assertEquals("TG028993-590A", TagParser.stand(raw).partNo)
            assertEquals("TG028993-590A", TagParser.box(raw).partNo)
            assertTrue(TagParser.stand(raw).success)
            assertTrue(TagParser.box(raw).success)
        }
        assertEquals("TGY94159-0010", TagParser.stand("TGY94159-0010").partNo)
        assertEquals("TGY94159-0010", TagParser.box("TGY94159-0010").partNo)
    }

    @Test fun plainDnthWorksWithNewKanbanAndRejectsWrongPart() {
        val stand = TagParser.stand("TG028993-590 A")
        val box = TagParser.box("TG028993-590 A")
        val kanban = TagParser.kanban(discSample)
        assertTrue(TagParser.partsMatch(stand.partNo!!, box.partNo!!))
        assertTrue(TagParser.partsMatch(box.partNo!!, kanban.partNo!!))
        assertEquals(PartComparison.WARNING, TagParser.compareParts(TagParser.box("TG028993-590B").partNo!!, kanban.partNo!!).result)
    }

    @Test fun plainDnthDoesNotGuessFromOtherDocumentsOrMalformedValues() {
        for (raw in listOf("", " \u00a0", "| \u00a0", "EMPLOYEE|Mr.Burin", discSample,
            "TG028993-590A TG028993-590B", "TG028993-590A_", "TG028993-590", "PREFIX TG028993-590A")) {
            assertFalse("Stand accepted: $raw", TagParser.stand(raw).success)
            assertFalse("Box accepted: $raw", TagParser.box(raw).success)
        }
    }

    @Test fun discUsesBottomPartWhenLongSuffixAndQuantityAreJoined() {
        val raw = "DISC5060020000010101000210125104151120710725124061290515207154081550911                         TG053661-7020S20000100                         C07        3036996                 T-1          60903373  TG053661-7020S2     01"
        val result = TagParser.kanban(raw)

        assertTrue(result.success)
        assertEquals("TG053661-7020S2", result.partNo)
        assertEquals("dnth_disc_bottom_tg_primary", result.ruleId)
        assertEquals("5.0", result.ruleVersion)
        assertTrue(TagParser.partsMatch(result.partNo!!, TagParser.stand("TG053661-7020S2").partNo!!))
        assertTrue(TagParser.partsMatch(result.partNo!!, TagParser.box("ITG053661-7020S2").partNo!!))
    }

    @Test fun discBottomPartAlsoKeepsExistingFourCharacterSuffix() {
        val raw = "DISC5060020000010101000210125104151120710725124061290515207154081550911                         TG028993-6160 0000020                         C07        3012062                              60903373  TG028993-6160       01"
        val result = TagParser.kanban(raw)

        assertTrue(result.success)
        assertEquals("TG028993-6160", result.partNo)
        assertEquals("dnth_disc_bottom_tg_primary", result.ruleId)
    }

    @Test fun discAcceptsDnthLaneWithOrWithoutHyphenOrSpace() {
        val base = "DISC5060020000010101000210125104151120710725124061290515207154081550911 TG028383-0090 0000030 C07 3901665 %s 60905059 TG028383-0090 01"
        for (lane in listOf("T1", "T-1", "T 1")) {
            val result = TagParser.kanban(base.format(lane))
            assertTrue("Lane $lane was rejected: ${result.message}", result.success)
            assertEquals("TG028383-0090", result.partNo)
            assertEquals("5.0", result.ruleVersion)
        }
    }

    @Test fun discRejectsLongSuffixWhenUpperAndBottomPartsDiffer() {
        val raw = "DISC5060020000010101000210125104151120710725124061290515207154081550911 TG053661-7020S20000100 C07 3036996 T-1 60903373 TG053661-7020S3 01"
        assertFalse(TagParser.kanban(raw).success)
    }

    @Test fun plainDnthFixPreservesExistingStandBoxFormats() {
        assertEquals("TG028993-590A", TagParser.stand("|TG028993-590 A").partNo)
        assertEquals("TG028993-590A", TagParser.box("|TG028993-590 A").partNo)
        assertEquals("TG028993-590A", TagParser.box("ITG028993-590 A").partNo)
        assertEquals("TG028993-590A", TagParser.box("PD26080101|FP01|PART|TG028993-590 A|40|PCS").partNo)
        assertEquals("JGC123456-40", TagParser.stand("|JGC123456-40").partNo)
    }

    @Test fun discUsesTgCaseInsensitivelyWithoutDependingOnLaneOrC07() {
        val raw = "DISC506002000001 tg028383-0040 0000030 3007010 60910340 tg028383-0040 01"
        val result = TagParser.kanban(raw)
        assertTrue(result.message, result.success)
        assertEquals("TG028383-0040", result.partNo)
        assertEquals("dnth_disc_bottom_tg_primary", result.ruleId)
    }

    @Test fun jtcsB01AttachedPartIsParsedAndComparedInThreeLevels() {
        val raw = "B01JGD10-0001230-400000010021130202774 5211302 0038338z"
        val kanban = TagParser.kanban(raw)
        assertTrue(kanban.message, kanban.success)
        assertEquals("JGD10-0001230-40", kanban.partNo)
        assertEquals("KANBAN_JTCS", kanban.tagType)
        assertEquals(PartComparison.WARNING,
            TagParser.compareParts(kanban.partNo!!, "JGD10-001230-40-0").result)
        assertEquals(PartComparison.MISMATCH,
            TagParser.compareParts(kanban.partNo!!, "JGF02-001230-40-0").result)
        assertEquals(PartComparison.EXACT,
            TagParser.compareParts("jgd10-001230-40", "JGD10-001230-40").result)
    }
}
