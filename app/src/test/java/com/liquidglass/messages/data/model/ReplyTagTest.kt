package com.liquidglass.messages.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyTagTest {

    private fun msg(id: Long, body: String, out: Boolean, t: Long) =
        Message(id = id, threadId = 1, address = "+1", body = body, timestamp = t, isOutgoing = out)

    @Test
    fun formatAndParseRoundTrip() {
        val original = msg(1, "Can you bring the charger tonight please?", false, 1)
        val wire = ReplyTag.format(original, "Sure, both of them!")
        assertTrue(wire.startsWith("↪ «Can you bring the charger ton…»\n"))
        val parsed = ReplyTag.parse(wire)
        assertEquals("Sure, both of them!", parsed.text)
        assertEquals("Can you bring the charger ton…", parsed.quote)
    }

    @Test
    fun receiverFindsTheQuotedMessage() {
        val thread = listOf(
            msg(1, "Can you bring the charger tonight please?", true, 10),
            msg(2, "Also the book", true, 20),
            msg(3, "↪ «Can you bring the charger ton…»\nSure!", false, 30),
        )
        val quote = ReplyTag.parse(thread[2].body).quote!!
        assertEquals(1L, ReplyTag.findQuoted(quote, thread[2], thread)!!.id)
    }

    @Test
    fun worksWithPersianEffectsAndLocations() {
        val loc = msg(1, "📍 My Location\nhttps://maps.google.com/?q=35.7,51.4", false, 1)
        assertEquals("📍 My Location", ReplyTag.snippet(loc))
        val fa = msg(2, "سلام! فردا ساعت ۵ می‌بینمت؟\n(Sent with Big effect)", false, 2)
        assertEquals("سلام! فردا ساعت ۵ می‌بینمت؟", ReplyTag.snippet(fa))

        val reply = "↪ «سلام! فردا ساعت ۵ می‌بینمت؟»\nآره حتماً\n(Sent with Shake effect)"
        assertEquals("آره حتماً", MessageText.visible(reply))
        val thread = listOf(loc, fa, msg(3, reply, true, 3))
        assertEquals(2L, ReplyTag.findQuoted(ReplyTag.parse(reply).quote!!, thread[2], thread)!!.id)
    }

    @Test
    fun ordinaryTextIsUntouched() {
        assertNull(ReplyTag.parse("hello «world»").quote)
        assertEquals("hello «world»", MessageText.visible("hello «world»"))
    }
}
