package com.tskforging.checktagrs

object TagParser {
    private fun clean(raw: String) = raw.removeSuffix("\r\n").removeSuffix("\n").removeSuffix("\r")

    /** Removes print/scanner whitespace without changing meaningful Part No. characters. */
    fun normalizePart(value: String): String = value
        .filterNot { it.isWhitespace() || Character.isSpaceChar(it) }
        .uppercase()

    /** Full normalized value. Customer suffixes are no longer silently ignored. */
    fun comparisonPart(value: String): String = normalizePart(value)

    fun compareParts(expected: String, actual: String): PartComparisonResult {
        val e = comparisonPart(expected)
        val a = comparisonPart(actual)
        if (e == a) return PartComparisonResult(
            PartComparison.EXACT, e, a, "Part No. ตรงกันทุกตัวอักษร"
        )
        val eFamily = e.substringBefore('-')
        val aFamily = a.substringBefore('-')
        val sameFamily = '-' in e && '-' in a && eFamily == aFamily
        return if (sameFamily) PartComparisonResult(
            PartComparison.WARNING, e, a,
            "กลุ่ม $eFamily ตรงกัน แต่รายละเอียดหลังเครื่องหมาย - ต่างกัน\n${firstDifference(e, a)}"
        ) else PartComparisonResult(
            PartComparison.MISMATCH, e, a,
            "กลุ่ม Part No. ไม่ตรงกัน: $eFamily ≠ $aFamily"
        )
    }

    fun partsMatch(expected: String, actual: String): Boolean =
        compareParts(expected, actual).let {
            it.result == PartComparison.EXACT ||
                (it.result == PartComparison.WARNING && it.expected.startsWith("J"))
        }

    // Accept only a complete, known DNTH Part No.; never extract a substring
    // from a multi-field Kanban, employee QR, or arbitrary label text.
    private fun plainDnthPart(raw: String): String? {
        val value = normalizePart(raw)
        return value.takeIf { Regex("^(?:TG\\d{6}|TGY\\d{5})-[A-Z0-9]{4,10}$").matches(it) }
    }

    fun stand(rawInput: String): ParseResult {
        val raw = clean(rawInput)
        val fields = raw.split('|')
        if (fields.size == 2 && fields[0].isEmpty() && normalizePart(fields[1]).isNotEmpty())
            return ParseResult(true, normalizePart(fields[1]), "STAND", "stand_pipe_field_2_normalized", "2.0")
        plainDnthPart(raw)?.let {
            return ParseResult(true, it, "STAND", "stand_dnth_plain_part", "1.0")
        }
        return ParseResult(false, null, "UNKNOWN", "stand_auto", "2.0",
            "Stand ต้องเป็น |PART-NO หรือรหัส DNTH เช่น TG028993-590A หากยังอ่านไม่ได้ ให้เปิด RAW DATA")
    }

    fun box(rawInput: String): ParseResult {
        val raw = clean(rawInput)
        val fields = raw.split('|')
        if (fields.size == 2 && fields[0].isEmpty() && normalizePart(fields[1]).isNotEmpty())
            return ParseResult(true, normalizePart(fields[1]), "PLASTIC_BOX", "plastic_pipe_field_2_normalized", "2.0")
        if (raw.startsWith("PD") && fields.size >= 4 && normalizePart(fields[3]).isNotEmpty())
            return ParseResult(true, normalizePart(fields[3]), "FG_TAG", "fg_pipe_field_4_normalized", "2.0")
        // DNTH box labels may add an "I" prefix to the Kanban Part No.
        // Example: ITG028351-5130 on BOX TAG matches TG028351-5130 on KANBAN.
        val dnthBox = Regex("^I(TG\\d{6}-[A-Z0-9]{4,10}|TGY\\d{5}-[A-Z0-9]{4,10})$", RegexOption.IGNORE_CASE)
            .matchEntire(normalizePart(raw))
        if (dnthBox != null)
            return ParseResult(true, dnthBox.groupValues[1].uppercase(), "DNTH_BOX", "dnth_i_prefix_removed", "2.1")
        plainDnthPart(raw)?.let {
            return ParseResult(true, it, "DNTH_BOX", "box_dnth_plain_part", "1.0")
        }
        return ParseResult(false, null, "UNKNOWN", "box_auto", "1.1",
            "ไม่รู้จักรูปแบบ Box Tag รองรับ |PART-NO, FG Tag, รหัส DNTH และ I+รหัส DNTH กรุณาเปิด RAW DATA")
    }

    fun kanban(rawInput: String): ParseResult {
        val raw = clean(rawInput)
        if (raw.isBlank())
            return ParseResult(false, null, "UNKNOWN", "kanban_customer_auto", "1.0", "Kanban ว่าง")

        // AISIN confirmed sample contains 0 + seven digits + hyphen + five digits.
        // The leading zero is a Kanban prefix and is not part of the Part No.
        val upperRaw = raw.map {
            if (it.isWhitespace() || Character.isSpaceChar(it)) ' ' else it
        }.joinToString("").uppercase()
        // DISC must be parsed by field position, before other customer rules.
        // Never fall back to searching all Part Nos. if a DISC record is malformed.
        if (upperRaw.trimStart().startsWith("DISC")) return dnthDisc(upperRaw.trim())
        // New Aisin format: prefix 01 + a five-digit/five-digit Part No.
        val aisinShortMatches = Regex(
            "(?<!\\d)01\\s*(\\d{5}\\s*-\\s*\\d{5})(?!\\d)"
        ).findAll(upperRaw)
            .map { normalizePart(it.groupValues[1]) }
            .distinct()
            .toList()
        if (aisinShortMatches.size == 1)
            return ParseResult(true, aisinShortMatches.first(), "KANBAN_AISIN", "aisin_01_short_part_normalized", "2.0")
        if (aisinShortMatches.size > 1)
            return ParseResult(false, null, "KANBAN_AISIN", "aisin_01_short_part_normalized", "2.0", "พบ Part No. Aisin มากกว่า 1 ค่าที่ไม่ตรงกัน")

        val aisinMatches = Regex(
            "(?<!\\d)0\\s*(\\d{7}\\s*-\\s*\\d{5})(?!\\d)"
        ).findAll(upperRaw)
            .map { normalizePart(it.groupValues[1]) }
            .distinct()
            .toList()
        if (aisinMatches.size == 1)
            return ParseResult(true, aisinMatches.first(), "KANBAN_AISIN", "aisin_leading_zero_part_normalized", "2.0")
        if (aisinMatches.size > 1)
            return ParseResult(false, null, "KANBAN_AISIN", "aisin_leading_zero_part", "1.0", "พบ Part No. Aisin มากกว่า 1 ค่าที่ไม่ตรงกัน")

        // DNTH prints the same Part No. twice. Both copies must agree.
        // Supported families: legacy TGY#####-#### and TG######-####.
        // Keep separators while locating DNTH values. Searching `compact` would
        // join the preceding/following fields to TGY and break the boundaries.
        val dnthMatches = Regex("(?<![A-Z0-9])(?:TGY\\d{5}|TG\\d{6})-\\d{4}(?![A-Z0-9])")
            .findAll(raw.uppercase()).map { normalizePart(it.value) }.toList()
        if (dnthMatches.size >= 2 && dnthMatches.distinct().size == 1)
            return ParseResult(true, dnthMatches.first(), "KANBAN_DNTH", "dnth_repeated_part", "2.1")
        if (dnthMatches.isNotEmpty())
            return ParseResult(false, null, "KANBAN_DNTH", "dnth_repeated_part", "2.1", "Part No. DNTH ต้องพบซ้ำอย่างน้อย 2 ตำแหน่งและต้องตรงกัน")

        // JTCS legacy Kanban can join B01 directly to the Part No. and can
        // print seven digits in its first numeric section.
        val jtcs = Regex(
            "B01\\s*(J[A-Z]{2}\\d{2}-\\d{6,7}-[A-Z0-9]{2})",
            RegexOption.IGNORE_CASE
        ).find(raw)
        if (jtcs != null)
            return ParseResult(true, normalizePart(jtcs.groupValues[1]), "KANBAN_JTCS", "jtcs_b01_part", "1.0")

        // JTEKT/JATH parsing keeps the full Part No. Comparison later decides
        // whether it is an exact match, same-family warning, or mismatch.
        val jathMatches = Regex("(?<![A-Z0-9])(J[A-Z]{2}(?:\\d{6}(?:-[A-Z0-9]+)*|\\d{2}-\\d{6}-\\d{2}(?:-[A-Z0-9]+)*))(?![A-Z0-9-])")
            .findAll(raw.uppercase())
            .map { normalizePart(it.value) }
            .distinct()
            .toList()
        if (jathMatches.size == 1)
            return ParseResult(true, jathMatches.first(), "KANBAN_JATH", "jtekt_before_first_hyphen", "2.0")
        if (jathMatches.size > 1)
            return ParseResult(false, null, "KANBAN_JATH", "jtekt_before_first_hyphen", "2.0", "พบ Part No. JTEKT มากกว่า 1 ค่าที่ไม่ตรงกัน")

        // SNSS QR sample: 7521T0376  260805  80\nCLM012
        // Field 1 is Part No.; field 2 is YYMMDD, field 3 is quantity,
        // and the final field is the CLM reference.
        val snss = Regex(
            "^([A-Z0-9][A-Z0-9-]{3,})\\s+(\\d{6})\\s+(\\d+)\\s+(CLM[A-Z0-9-]+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(raw.trim().replace(Regex("\\s+"), " "))
        if (snss != null)
            return ParseResult(true, normalizePart(snss.groupValues[1]), "KANBAN_SNSS", "snss_part_date_qty_clm", "1.0")

        return ParseResult(false, null, "UNKNOWN", "kanban_customer_auto", "1.0", "ยังไม่มีกติกาสำหรับ Kanban รูปแบบนี้")
    }

    /**
     * DNTH DISC uses the TG/TGY value immediately before the final 01 as its
     * lower-row reference. C07, lane text and whitespace are optional metadata;
     * they are deliberately not parsing anchors.
     */
    private fun dnthDisc(raw: String): ParseResult {
        val spacedPart = "(?:T\\s*G\\s*Y(?:\\s*\\d){5}|T\\s*G(?:\\s*\\d){6})\\s*-(?:\\s?[A-Z0-9]){4,10}?"
        val bottom = Regex("($spacedPart)\\s+01$", RegexOption.IGNORE_CASE).find(raw)
            ?: return ParseResult(false, null, "KANBAN_DNTH", "dnth_disc_bottom_part", "4.1",
                "ไม่พบ Part No. แถวล่างก่อน 01")
        val repeatedCustomer = normalizePart(bottom.groupValues[1])
        val beforeBottom = raw.substring(0, bottom.range.first).trimEnd()
        if (Regex("(?:T\\s*G\\s*Y(?:\\s*\\d){5}|T\\s*G(?:\\s*\\d){6})\\s*-(?:\\s*[A-Z0-9]){4,10}_", RegexOption.IGNORE_CASE).containsMatchIn(beforeBottom)
                || Regex("(?<![A-Z0-9])[A-Z]{2,}\\s*-\\s*[A-Z]{2,}(?![A-Z0-9])", RegexOption.IGNORE_CASE).containsMatchIn(beforeBottom))
            return ParseResult(false, null, "KANBAN_DNTH", "dnth_disc_tg_primary", "5.0",
                "พบข้อมูลคล้าย Part No. แต่รูปแบบไม่ถูกต้อง")
        val beforeCompact = normalizePart(beforeBottom)
        val upperCandidates = Regex(
            "($spacedPart)(?=\\s{2,}|\\s+T\\s*G|\\s+\\d{6,}(?:\\s|$)|\\s+C07(?:\\s|$)|$)",
            RegexOption.IGNORE_CASE
        )
            .findAll(beforeBottom)
            .map { normalizePart(it.groupValues[1]) }
            .map { if (it.startsWith(repeatedCustomer)) repeatedCustomer else it }
            .distinct()
            .toList()
        val hasRepeatedUpper = beforeCompact.contains(repeatedCustomer)
        val hasAnyUpperTg = Regex("(?:TGY\\d{5}|TG\\d{6})-").containsMatchIn(beforeCompact)
        if (!hasRepeatedUpper && (upperCandidates.isNotEmpty() || hasAnyUpperTg))
            return ParseResult(false, null, "KANBAN_DNTH", "dnth_disc_tg_primary", "5.0",
                "Part No. แถวบนไม่ตรงกับ Part No. แถวล่าง")
        val boxCandidates = upperCandidates.filter { it != repeatedCustomer }
        if (boxCandidates.size > 1)
            return ParseResult(false, null, "KANBAN_DNTH", "dnth_disc_tg_primary", "5.0",
                "พบ Part No. แถวบนมากกว่า 1 ค่าที่ไม่ตรงกัน")
        val selected = boxCandidates.singleOrNull() ?: repeatedCustomer
        return ParseResult(true, selected, "KANBAN_DNTH",
            if (selected == repeatedCustomer) "dnth_disc_bottom_tg_primary" else "dnth_disc_box_tg_primary", "5.0")
    }

    fun firstDifference(expected: String, actual: String): String {
        val expectedForCompare = comparisonPart(expected)
        val actualForCompare = comparisonPart(actual)
        val common = minOf(expectedForCompare.length, actualForCompare.length)
        val i = (0 until common).firstOrNull { expectedForCompare[it] != actualForCompare[it] } ?: common
        if (i == expectedForCompare.length && i == actualForCompare.length) return "ตรงกันทุกตัวอักษร"
        val e = expectedForCompare.getOrNull(i)?.toString() ?: "<ไม่มี>"
        val a = actualForCompare.getOrNull(i)?.toString() ?: "<ไม่มี>"
        return "ต่างกันที่ตำแหน่ง ${i + 1}: ควรเป็น [$e] แต่อ่านได้ [$a]"
    }
}
