package com.tskforging.checktagrs

object TagParser {
    private fun clean(raw: String) = raw.removeSuffix("\r\n").removeSuffix("\n").removeSuffix("\r")

    /** Removes print/scanner whitespace without changing meaningful Part No. characters. */
    fun normalizePart(value: String): String = value
        .filterNot { it.isWhitespace() || Character.isSpaceChar(it) }
        .uppercase()

    /**
     * Returns the value used only for comparison. For JTEKT/JATH Part Nos.,
     * every character from the first '-' onward is a label-specific suffix
     * and must be ignored across Stand, Box and Kanban tags.
     */
    fun comparisonPart(value: String): String {
        val normalized = normalizePart(value)
        val isJtekt = Regex("^J[A-Z]{2}(?:\\d{6}|\\d{2}-\\d{6}-\\d{2})(?:-[A-Z0-9]+)*$")
            .matches(normalized)
        return if (isJtekt) normalized.substringBefore('-') else normalized
    }

    fun partsMatch(expected: String, actual: String): Boolean =
        comparisonPart(expected) == comparisonPart(actual)

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

        // JTEKT/JATH comparison ignores everything from the first '-' onward,
        // regardless of whether the suffix is printed on Stand, Box or Kanban.
        val jathMatches = Regex("(?<![A-Z0-9])(J[A-Z]{2}(?:\\d{6}(?:-[A-Z0-9]+)*|\\d{2}-\\d{6}-\\d{2}(?:-[A-Z0-9]+)*))(?![A-Z0-9-])")
            .findAll(raw.uppercase())
            .map { comparisonPart(it.value) }
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

    /** Confirmed DISC layouts: header, customer part, optional box part, quantity,
     * supplier C07, tag serial, optional lane, D/O, repeated customer part, 01.
     * The unambiguous repeated Part No. immediately before 01 is parsed first.
     * It then anchors the upper row, including layouts where the 7-digit quantity
     * is printed directly after a variable-length Part No. without whitespace.
     */
    private fun dnthDisc(raw: String): ParseResult {
        val spacedPart = "(?:T\\s*G\\s*Y(?:\\s*\\d){5}|T\\s*G(?:\\s*\\d){6})\\s*-(?:\\s*[A-Z0-9]){4,10}"
        val bottom = Regex("($spacedPart)\\s+01$").find(raw)
            ?: return ParseResult(false, null, "KANBAN_DNTH", "dnth_disc_bottom_part", "4.0",
                "ไม่พบ Part No. แถวล่างก่อน 01")
        val repeatedCustomer = normalizePart(bottom.groupValues[1])
        val beforeBottom = raw.substring(0, bottom.range.first).trimEnd()
        val c07Index = beforeBottom.lastIndexOf("C07")
        if (c07Index < 0)
            return ParseResult(false, null, "KANBAN_DNTH", "dnth_disc_bottom_part", "4.0",
                "ไม่พบช่อง C07 ใน KANBAN DNTH")

        val upperCompact = normalizePart(beforeBottom.substring(0, c07Index))
        val afterC07 = beforeBottom.substring(c07Index).trim()
        if (!Regex("^C07\\s+\\d+\\s+(?:T-\\d+\\s+)?\\d+$").matches(afterC07))
            return ParseResult(false, null, "KANBAN_DNTH", "dnth_disc_bottom_part", "4.0",
                "ช่องข้อมูลระหว่าง C07 และ Part No. แถวล่างไม่ครบ")

        val anchoredUpper = Regex("^DISC\\d+${Regex.escape(repeatedCustomer)}(.*)$")
            .matchEntire(upperCompact)
            ?: return ParseResult(false, null, "KANBAN_DNTH", "dnth_disc_bottom_part", "4.0",
                "Part No. แถวบนไม่ตรงกับ Part No. แถวล่าง")
        val remainder = anchoredUpper.groupValues[1]
        val boxAndQuantity = Regex("^((?:TGY\\d{5}|TG\\d{6})-[A-Z0-9]{4,10})(\\d{7})$")
            .matchEntire(remainder)
        val boxPart = when {
            Regex("^\\d{7}$").matches(remainder) -> ""
            boxAndQuantity != null -> boxAndQuantity.groupValues[1]
            else -> return ParseResult(false, null, "KANBAN_DNTH", "dnth_disc_bottom_part", "4.0",
                "แยก Part No. แถวบนและจำนวน 7 หลักไม่ได้")
        }
        return ParseResult(true, boxPart.ifEmpty { repeatedCustomer }, "KANBAN_DNTH",
            if (boxPart.isEmpty()) "dnth_disc_bottom_part" else "dnth_disc_box_part_before_qty", "4.0")
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
