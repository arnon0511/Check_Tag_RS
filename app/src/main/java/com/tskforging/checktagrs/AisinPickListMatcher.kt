package com.tskforging.checktagrs

data class AisinDocumentMatch(
    val success: Boolean,
    val pickJcc: String? = null,
    val kanbanJcc: String? = null,
    val groupCode: String? = null,
    val routeCode: String? = null,
    val message: String = ""
)

object AisinPickListMatcher {
    private val jcc = Regex("JCC\\d{11,}", RegexOption.IGNORE_CASE)
    private val group = Regex("J\\d{3}", RegexOption.IGNORE_CASE)
    private val route = Regex("\\d[A-Z]\\d{2}", RegexOption.IGNORE_CASE)

    fun compare(pickRaw: String, kanbanRaw: String): AisinDocumentMatch {
        val pick = compact(pickRaw)
        val kanban = compact(kanbanRaw)
        val pickJcc = jcc.find(pick)?.value?.uppercase()
        val kanbanJcc = jcc.find(kanban)?.value?.uppercase()
        if (pickJcc == null || kanbanJcc == null)
            return AisinDocumentMatch(false, pickJcc, kanbanJcc, message="ไม่พบเลขอ้างอิง JCC ใน Pick List หรือ KANBAN")

        val commonGroup = group.findAll(kanban).map { it.value.uppercase() }
            .firstOrNull { pick.contains(it) }
        val commonRoute = route.findAll(kanban).map { it.value.uppercase() }
            .firstOrNull { pick.contains(it) }
        if (!pickJcc.startsWith(kanbanJcc))
            return AisinDocumentMatch(false, pickJcc, kanbanJcc, commonGroup, commonRoute, "เลขอ้างอิง JCC ไม่ตรงกัน")
        if (commonGroup == null || commonRoute == null)
            return AisinDocumentMatch(false, pickJcc, kanbanJcc, commonGroup, commonRoute, "รหัสกลุ่มหรือรหัสเส้นทางไม่ตรงกัน")
        return AisinDocumentMatch(true, pickJcc, kanbanJcc, commonGroup, commonRoute, "JCC, รหัสกลุ่ม และรหัสเส้นทางตรงกัน")
    }

    private fun compact(raw: String) = raw.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }.uppercase()
}
