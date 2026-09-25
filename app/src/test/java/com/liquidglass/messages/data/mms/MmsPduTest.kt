package com.liquidglass.messages.data.mms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsPduTest {

    private fun notificationBytes(subject: String = "سلام", withCharsetFrom: Boolean = true): ByteArray {
        val w = MmsPdu.Writer()
        w.u8(MmsPdu.H_MESSAGE_TYPE); w.u8(MmsPdu.TYPE_NOTIFICATION_IND)
        w.u8(MmsPdu.H_TRANSACTION_ID); w.textString("T12345")
        w.u8(MmsPdu.H_MMS_VERSION); w.u8(MmsPdu.VERSION_1_2)
        // From: value-length, address-present, encoded-string (optionally with charset).
        val from = MmsPdu.Writer()
        from.u8(0x80)
        if (withCharsetFrom) {
            val inner = MmsPdu.Writer(); inner.u8(0xEA); inner.textString("+989121234567/TYPE=PLMN")
            from.valueLength(inner.size()); from.bytes(inner.toByteArray())
        } else {
            from.textString("+989121234567/TYPE=PLMN")
        }
        w.u8(MmsPdu.H_FROM); w.valueLength(from.size()); w.bytes(from.toByteArray())
        w.u8(MmsPdu.H_SUBJECT); w.encodedString(subject)
        w.u8(MmsPdu.H_MESSAGE_CLASS); w.u8(0x80)
        w.u8(MmsPdu.H_MESSAGE_SIZE); w.longInteger(48_213)
        // Expiry: relative 7 days.
        val exp = MmsPdu.Writer(); exp.u8(0x81); exp.longInteger(604_800)
        w.u8(MmsPdu.H_EXPIRY); w.valueLength(exp.size()); w.bytes(exp.toByteArray())
        w.u8(MmsPdu.H_CONTENT_LOCATION); w.textString("http://mms.carrier.example/get?id=abc")
        return w.toByteArray()
    }

    @Test
    fun parsesNotification() {
        val n = MmsPdu.parseNotification(notificationBytes())
        assertNotNull(n)
        n!!
        assertEquals("T12345", n.transactionId)
        assertEquals("http://mms.carrier.example/get?id=abc", n.contentLocation)
        assertEquals("+989121234567", n.from)
        assertEquals("سلام", n.subject)
        assertEquals(48_213L, n.messageSize)
        assertEquals(604_800L, n.expiry)
    }

    @Test
    fun parsesNotificationWithPlainFrom() {
        val n = MmsPdu.parseNotification(notificationBytes(subject = "Hi", withCharsetFrom = false))
        assertEquals("+989121234567", n!!.from)
        assertEquals("Hi", n.subject)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(MmsPdu.parseNotification(byteArrayOf(0x8C.toByte(), 0x84.toByte())))
        assertNull(MmsPdu.parseNotification(byteArrayOf(1, 2, 3)))
        assertNull(MmsPdu.parseRetrieveConf(byteArrayOf()))
    }

    @Test
    fun sendReqRoundTripsThroughTheRetrieveParser() {
        val image = ByteArray(5000) { (it * 31).toByte() }
        val text = "Hello سلام 👋"
        val pdu = MmsPdu.encodeSendReq(
            to = listOf("+98 912 123 4567", "+15551234567"),
            parts = listOf(
                MmsPdu.Part("image/jpeg", image),
                MmsPdu.Part(MmsPdu.CT_TEXT, text.toByteArray(Charsets.UTF_8), charset = MmsPdu.CHARSET_UTF8),
            ),
            transactionId = "tx-1",
            subject = "Photo",
        )
        // Same header grammar as a retrieve-conf; flip the type byte to reuse the parser.
        assertEquals(MmsPdu.H_MESSAGE_TYPE, pdu[0].toInt() and 0xFF)
        val asRetrieve = pdu.copyOf().also { it[1] = MmsPdu.TYPE_RETRIEVE_CONF.toByte() }
        val r = MmsPdu.parseRetrieveConf(asRetrieve)
        assertNotNull(r)
        r!!
        assertEquals(listOf("+989121234567", "+15551234567"), r.to)
        assertEquals("Photo", r.subject)
        assertEquals(MmsPdu.CT_MULTIPART_RELATED, r.contentType)
        assertEquals(3, r.parts.size)

        val img = r.parts.first { it.contentType == "image/jpeg" }
        assertArrayEquals(image, img.data)
        val txt = r.parts.first { it.contentType == MmsPdu.CT_TEXT }
        assertEquals(text, txt.text())
        val smil = r.parts.first { it.contentType == MmsPdu.CT_SMIL }
        assertTrue(smil.text().contains("<img src=\"${img.name}\""))
        // The SMIL start part is ordered last for readers.
        assertEquals(MmsPdu.CT_SMIL, r.parts.last().contentType)
    }

    @Test
    fun uintvarAndLengthsHandleLargeParts() {
        val big = ByteArray(300_000) { it.toByte() }
        val pdu = MmsPdu.encodeSendReq(listOf("+1555"), listOf(MmsPdu.Part("video/mp4", big)), "t")
        val r = MmsPdu.parseRetrieveConf(pdu.copyOf().also { it[1] = MmsPdu.TYPE_RETRIEVE_CONF.toByte() })!!
        assertArrayEquals(big, r.parts.first { it.contentType == "video/mp4" }.data)
    }

    @Test
    fun parsesSendConf() {
        val w = MmsPdu.Writer()
        w.u8(MmsPdu.H_MESSAGE_TYPE); w.u8(MmsPdu.TYPE_SEND_CONF)
        w.u8(MmsPdu.H_TRANSACTION_ID); w.textString("tx-1")
        w.u8(MmsPdu.H_MMS_VERSION); w.u8(MmsPdu.VERSION_1_2)
        w.u8(MmsPdu.H_RESPONSE_STATUS); w.u8(MmsPdu.RESPONSE_OK)
        w.u8(MmsPdu.H_MESSAGE_ID); w.textString("MSGID-9")
        val c = MmsPdu.parseSendConf(w.toByteArray())!!
        assertEquals(MmsPdu.RESPONSE_OK, c.responseStatus)
        assertEquals("MSGID-9", c.messageId)
    }

    @Test
    fun notifyRespIsWellFormed() {
        val b = MmsPdu.encodeNotifyResp("T1")
        assertEquals(0x8C, b[0].toInt() and 0xFF)
        assertEquals(MmsPdu.TYPE_NOTIFYRESP_IND, b[1].toInt() and 0xFF)
        assertEquals(MmsPdu.STATUS_RETRIEVED, b.last().toInt() and 0xFF)
    }

    @Test
    fun addressesAreNormalised() {
        assertEquals("+989121234567/TYPE=PLMN", MmsPdu.toMmsAddress("+98 912-123-4567"))
        assertEquals("a@b.com", MmsPdu.toMmsAddress("a@b.com"))
        assertEquals("+1555", MmsPdu.normalizeAddress("+1555/TYPE=PLMN"))
    }
}
