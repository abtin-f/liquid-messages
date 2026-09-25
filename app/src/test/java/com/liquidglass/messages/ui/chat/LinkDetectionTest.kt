package com.liquidglass.messages.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkDetectionTest {

    private fun urls(text: String) = detectLinks(text).map { it.url }

    @Test
    fun findsWebLinks() {
        val found = urls("see https://github.com/x/y and www.digikala.com please")
        assertTrue(found.any { it.startsWith("https://github.com/x/y") })
        assertTrue(found.any { it.contains("www.digikala.com") })
    }

    @Test
    fun findsLinksInsidePersianText() {
        val text = "سلام این لینک رو ببین https://example.com/page ممنون"
        val link = detectLinks(text).single()
        assertEquals("https://example.com/page", text.substring(link.start, link.end))
    }

    @Test
    fun findsEmailAndPhone() {
        val found = urls("mail me at abtin@example.com or call 09121234567")
        assertTrue(found.any { it == "mailto:abtin@example.com" })
        assertTrue(found.any { it.startsWith("tel:") && it.filter(Char::isDigit).endsWith("9121234567") })
    }

    @Test
    fun locationMessageLinkIsTappable() {
        val found = urls("📍 My Location\nhttps://maps.google.com/?q=35.689200,51.389000")
        assertTrue(found.single().startsWith("https://maps.google.com/?q=35.689200,51.389000"))
    }

    @Test
    fun plainTextHasNoLinks() {
        assertTrue(detectLinks("see you at 5, ok?").isEmpty())
        assertTrue(detectLinks("Your code is 48291375").isEmpty())
        assertTrue(detectLinks("").isEmpty())
    }
}
