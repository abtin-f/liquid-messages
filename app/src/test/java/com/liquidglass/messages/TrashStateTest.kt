package com.liquidglass.messages

import com.liquidglass.messages.data.local.TrashStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashStateTest {
    private val state = TrashStore.State(
        threads = mapOf(7L to TrashStore.TrashedThread(7, cutoff = 1_000, deletedAt = 1_000, address = "+1", name = null, snippet = "", messageCount = 3)),
        messages = mapOf(42L to TrashStore.TrashedMessage(42, threadId = 9, deletedAt = 5, address = "+2", snippet = "hi")),
    )

    @Test fun hidesMessagesUpToThreadCutoff() {
        assertTrue(state.hides(7, messageId = 1, timestamp = 999))
        assertTrue(state.hides(7, messageId = 2, timestamp = 1_000))
    }

    @Test fun newMessageAfterDeleteIsVisible() = assertFalse(state.hides(7, messageId = 3, timestamp = 1_001))

    @Test fun hidesSingleDeletedMessage() {
        assertTrue(state.hides(9, messageId = 42, timestamp = 50))
        assertFalse(state.hides(9, messageId = 43, timestamp = 50))
    }
}
