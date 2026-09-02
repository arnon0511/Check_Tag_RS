package com.tskforging.checktagrs

enum class BoxCountStatus { UNDER, MATCH, OVER }
object BoxCountEvaluator {
    fun status(expected:Int,actual:Int)=when {
        actual<expected -> BoxCountStatus.UNDER
        actual>expected -> BoxCountStatus.OVER
        else -> BoxCountStatus.MATCH
    }
}
