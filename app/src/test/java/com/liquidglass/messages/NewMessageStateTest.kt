package com.liquidglass.messages

import com.liquidglass.messages.data.model.Contact
import com.liquidglass.messages.ui.newmessage.NewMessageUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewMessageStateTest {
    @Test fun typedNumberCountsAsRecipient() {
        val s = NewMessageUiState(recipient = "0912 111 2233", picked = listOf(Contact("+15551234567", "Olivia")))
        assertEquals(listOf("+15551234567", "09121112233"), s.addresses)
        assertTrue(s.isGroup)
    }

    @Test fun sameNumberInTwoFormatsIsOneRecipient() {
        val s = NewMessageUiState(recipient = "09121112233", picked = listOf(Contact("+989121112233", "Sara")))
        assertEquals(1, s.addresses.size)
        assertFalse(s.isGroup)
    }

    @Test fun addressesAreStrippedOfSpacesAndPunctuation() {
        val s = NewMessageUiState(picked = listOf(Contact(" +91 93705-33275 ", "Maithil")))
        assertEquals(listOf("+919370533275"), s.addresses)
        val t = NewMessageUiState(picked = listOf(Contact("(937) 053 3275", "Maithil")))
        assertEquals(listOf("9370533275"), t.addresses)
    }

    @Test fun nameBeingTypedIsNotARecipient() {
        val s = NewMessageUiState(recipient = "Sar")
        assertTrue(s.addresses.isEmpty())
    }
}
