package com.liquidglass.messages.data.mms

import org.robolectric.RuntimeEnvironment
import com.liquidglass.messages.MessagesApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MmsCoordinatorTest {

    private val app = RuntimeEnvironment.getApplication() as MessagesApplication
    private val mms get() = app.container.mms

    private fun retrieved(from: String?, to: List<String>, cc: List<String> = emptyList()) =
        MmsPdu.Retrieved(null, null, from, to, cc, 0, null, MmsPdu.CT_MULTIPART_RELATED, emptyList())

    @Test
    fun oneToOneThreadIsJustTheSender() {
        assertEquals(setOf("+989121111111"), mms.participantsOf(retrieved("+989121111111", listOf("+989350000000"))))
    }

    @Test
    fun groupLeavesOutOurOwnNumberLearnedFromA1to1Message() {
        // A 1:1 message teaches us our number (the only To).
        mms.participantsOf(retrieved("+989121111111", listOf("+989350000000")))
        // In a group, that number must not become a participant.
        val group = mms.participantsOf(retrieved("+989121111111", listOf("09350000000", "+989122222222", "+989123333333")))
        assertEquals(listOf("+989121111111", "+989122222222", "+989123333333"), group.toList())
    }

    @Test
    fun phoneComparisonIgnoresCountryCodeAndFormatting() {
        assertTrue(MmsCoordinator.samePhone("+98 912 111 1111", "09121111111"))
        assertFalse(MmsCoordinator.samePhone("+989121111111", "+989121111112"))
        assertTrue(MmsCoordinator.samePhone("a@b.com", "A@B.com"))
    }
}
