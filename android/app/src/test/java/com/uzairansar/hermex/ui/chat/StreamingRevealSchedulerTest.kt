package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingRevealSchedulerTest {
    @Test
    fun graphemeRangesKeepCombiningEmojiAndFlagsTogether() {
        val womanTechnologist = "\uD83D\uDC69\u200D\uD83D\uDCBB"
        val unitedStatesFlag = "\uD83C\uDDFA\uD83C\uDDF8"
        val text = "Ae\u0301$womanTechnologist$unitedStatesFlag"
        val rendered = streamingGraphemeRanges(text, 0).map { range ->
            text.substring(range.first, range.last + 1)
        }

        assertEquals(listOf("A", "e\u0301", womanTechnologist, unitedStatesFlag), rendered)
    }

    @Test
    fun schedulerCompressesLargeBatchesIntoMaximumLead() {
        val scheduler = StreamingRevealScheduler()
        val stamps = scheduler.schedule(
            text = "abcdefghij",
            start = 0,
            nowMillis = 1_000,
            graphemeStaggerMillis = 12,
            maximumLeadMillis = 45,
        )

        assertEquals(10, stamps.size)
        assertTrue(stamps.last().revealAtMillis <= 1_045)
        assertTrue(stamps.zipWithNext().all { (first, second) -> second.revealAtMillis >= first.revealAtMillis })
    }

    @Test
    fun revealAlphaUsesQuadraticEaseOut() {
        assertEquals(0, streamingRevealAlpha(ageMillis = 0, fadeDurationMillis = 350))
        assertTrue(streamingRevealAlpha(ageMillis = 175, fadeDurationMillis = 350) > 127)
        assertEquals(255, streamingRevealAlpha(ageMillis = 350, fadeDurationMillis = 350))
    }

    @Test
    fun complexMarkdownSpanClassesAreExcludedFromStreamingReveal() {
        assertTrue(streamingRevealExcludesSpanClassName("io.noties.markwon.core.spans.CodeBlockSpan"))
        assertTrue(streamingRevealExcludesSpanClassName("TableCellSpan"))
        assertTrue(streamingRevealExcludesSpanClassName("LatexDrawableSpan"))
        assertTrue(streamingRevealExcludesSpanClassName("MathSpan"))
        assertFalse(streamingRevealExcludesSpanClassName("StyleSpan"))
    }
}
