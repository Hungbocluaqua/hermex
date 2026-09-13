package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.ChatMessage

/** Reversible projection. An unmarked answer may still serve a pending human request. */
internal fun List<ChatMessage>.visibleTranscriptIndices(showTechnical: Boolean): Set<Int> =
    indices.filterTo(linkedSetOf()) { index ->
        val message = this[index]
        showTechnical || (message.displayKind != "process_wakeup" && message.source != "process_wakeup")
    }
