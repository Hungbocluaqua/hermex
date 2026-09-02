package com.uzairansar.hermex.ui.chat

import kotlin.math.roundToLong

internal data class StreamingRevealStamp(
    val start: Int,
    val end: Int,
    val revealAtMillis: Long,
)

internal class StreamingRevealScheduler {
    private var lastRevealAtMillis: Long = Long.MIN_VALUE

    fun reset() {
        lastRevealAtMillis = Long.MIN_VALUE
    }

    fun schedule(
        text: String,
        start: Int,
        nowMillis: Long,
        graphemeStaggerMillis: Int,
        maximumLeadMillis: Int,
    ): List<StreamingRevealStamp> {
        val ranges = streamingGraphemeRanges(text, start)
        if (ranges.isEmpty()) return emptyList()

        val stagger = graphemeStaggerMillis.coerceAtLeast(0).toDouble()
        val maximumLead = maximumLeadMillis.coerceAtLeast(0).toDouble()
        val firstReveal = minOf(
            maxOf(nowMillis.toDouble(), lastRevealAtMillis.toDouble() + stagger),
            nowMillis + maximumLead,
        )
        val availableLead = (nowMillis + maximumLead - firstReveal).coerceAtLeast(0.0)
        val pace = if (ranges.size <= 1) 0.0 else minOf(stagger, availableLead / (ranges.size - 1))

        return ranges.mapIndexed { index, range ->
            StreamingRevealStamp(
                start = range.first,
                end = range.last + 1,
                revealAtMillis = (firstReveal + pace * index).roundToLong(),
            )
        }.also { stamps ->
            lastRevealAtMillis = stamps.last().revealAtMillis
        }
    }
}

internal fun streamingRevealAlpha(ageMillis: Long, fadeDurationMillis: Int): Int {
    if (fadeDurationMillis <= 0 || ageMillis >= fadeDurationMillis) return 255
    if (ageMillis <= 0) return 0
    val progress = ageMillis.toDouble() / fadeDurationMillis
    val eased = 1.0 - (1.0 - progress) * (1.0 - progress)
    return (255.0 * eased).roundToLong().toInt().coerceIn(0, 255)
}

internal fun streamingGraphemeRanges(text: String, requestedStart: Int): List<IntRange> {
    if (text.isEmpty()) return emptyList()
    var index = requestedStart.coerceIn(0, text.length)
    if (index in 1 until text.length && text[index - 1].isHighSurrogate() && text[index].isLowSurrogate()) {
        index -= 1
    }
    val ranges = mutableListOf<IntRange>()
    while (index < text.length) {
        val clusterStart = index
        var regionalIndicatorCount = 0
        var codePoint = text.codePointAt(index)
        index += Character.charCount(codePoint)
        if (isRegionalIndicator(codePoint)) regionalIndicatorCount = 1

        while (index < text.length) {
            codePoint = text.codePointAt(index)
            when {
                isCombiningOrVariation(codePoint) || isEmojiModifier(codePoint) -> {
                    index += Character.charCount(codePoint)
                }
                codePoint == ZERO_WIDTH_JOINER -> {
                    index += Character.charCount(codePoint)
                    if (index < text.length) {
                        val joined = text.codePointAt(index)
                        index += Character.charCount(joined)
                    }
                }
                regionalIndicatorCount == 1 && isRegionalIndicator(codePoint) -> {
                    index += Character.charCount(codePoint)
                    regionalIndicatorCount += 1
                }
                else -> break
            }
        }
        ranges += clusterStart until index
    }
    return ranges
}

private fun isCombiningOrVariation(codePoint: Int): Boolean {
    val type = Character.getType(codePoint)
    return type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt() ||
        codePoint in 0xFE00..0xFE0F ||
        codePoint in 0xE0100..0xE01EF
}

private fun isEmojiModifier(codePoint: Int): Boolean = codePoint in 0x1F3FB..0x1F3FF

private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

private const val ZERO_WIDTH_JOINER = 0x200D
