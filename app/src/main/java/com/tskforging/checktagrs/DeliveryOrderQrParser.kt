package com.tskforging.checktagrs

data class DeliveryOrderPreset(
    val partNo: String,
    val currentQty: Int,
    val numberOfBoxes: Int
)

object DeliveryOrderQrParser {
    private const val PREFIX = "CHECKTAGRS|DO|"

    fun parse(rawInput: String): DeliveryOrderPreset? {
        val raw = rawInput.trim()
        if (!raw.startsWith(PREFIX, ignoreCase = true)) return null

        val values = raw.substring(PREFIX.length)
            .split('|')
            .mapNotNull { field ->
                val index = field.indexOf('=')
                if (index <= 0) null
                else field.substring(0, index).trim().uppercase() to field.substring(index + 1).trim()
            }
            .toMap()

        val part = values["PART"]?.let(TagParser::normalizePart).orEmpty()
        val qty = values["QTY"]?.filter(Char::isDigit)?.toIntOrNull()
        val boxes = values["BOX"]?.filter(Char::isDigit)?.toIntOrNull()
        if (part.isBlank() || qty == null || boxes == null || qty <= 0 || boxes <= 0) return null
        return DeliveryOrderPreset(part, qty, boxes)
    }
}
