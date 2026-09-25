package com.liquidglass.messages.data.mms

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Minimal, dependency-free codec for the MMS binary format (OMA-MMS-ENC 1.2 on
 * top of WAP-230 WSP encoding) — the subset a messaging app actually needs:
 *
 *  - parse  M-Notification.ind  (the WAP push that says "an MMS is waiting")
 *  - parse  M-Retrieve.conf     (the downloaded message: headers + multipart body)
 *  - parse  M-Send.conf         (the MMSC's answer to a send)
 *  - encode M-Send.req          (an outgoing message)
 *  - encode M-NotifyResp.ind    (acknowledging a download)
 *
 * Pure Kotlin on purpose: it is unit-tested on the JVM, and it keeps the app off
 * the hidden `com.google.android.mms` framework classes.
 */
object MmsPdu {

    // ---- Message types (X-Mms-Message-Type) ----
    const val TYPE_SEND_REQ = 0x80
    const val TYPE_SEND_CONF = 0x81
    const val TYPE_NOTIFICATION_IND = 0x82
    const val TYPE_NOTIFYRESP_IND = 0x83
    const val TYPE_RETRIEVE_CONF = 0x84

    // ---- MMS header field codes (already OR-ed with 0x80) ----
    internal const val H_BCC = 0x81
    internal const val H_CC = 0x82
    internal const val H_CONTENT_LOCATION = 0x83
    internal const val H_CONTENT_TYPE = 0x84
    internal const val H_DATE = 0x85
    internal const val H_DELIVERY_REPORT = 0x86
    internal const val H_EXPIRY = 0x88
    internal const val H_FROM = 0x89
    internal const val H_MESSAGE_CLASS = 0x8A
    internal const val H_MESSAGE_ID = 0x8B
    internal const val H_MESSAGE_TYPE = 0x8C
    internal const val H_MMS_VERSION = 0x8D
    internal const val H_MESSAGE_SIZE = 0x8E
    internal const val H_PRIORITY = 0x8F
    internal const val H_READ_REPORT = 0x90
    internal const val H_RESPONSE_STATUS = 0x92
    internal const val H_STATUS = 0x95
    internal const val H_SUBJECT = 0x96
    internal const val H_TO = 0x97
    internal const val H_TRANSACTION_ID = 0x98

    // ---- Values ----
    const val VERSION_1_2 = 0x92
    const val STATUS_RETRIEVED = 0x81
    const val RESPONSE_OK = 0x80
    private const val YES = 0x80
    private const val NO = 0x81
    private const val ADDRESS_PRESENT = 0x80
    private const val INSERT_ADDRESS = 0x81

    const val CHARSET_UTF8 = 106

    // ---- WSP part header field codes ----
    private const val P_CONTENT_LOCATION = 0x8E
    private const val P_CONTENT_ID = 0xC0

    // ---- WSP well-known parameters ----
    private const val PARAM_CHARSET = 0x81
    private const val PARAM_NAME_V1 = 0x85
    private const val PARAM_FILENAME_V1 = 0x86
    private const val PARAM_TYPE_REL = 0x89
    private const val PARAM_START_V1 = 0x8A
    private const val PARAM_NAME = 0x97
    private const val PARAM_FILENAME = 0x98
    private const val PARAM_START = 0x99

    /** WSP well-known content types (Assigned Numbers), index = code. */
    private val WELL_KNOWN_TYPES = arrayOf(
        "*/*", "text/*", "text/html", "text/plain", "text/x-hdml", "text/x-ttml",
        "text/x-vCalendar", "text/x-vCard", "text/vnd.wap.wml", "text/vnd.wap.wmlscript",
        "text/vnd.wap.wta-event", "multipart/*", "multipart/mixed", "multipart/form-data",
        "multipart/byterantes", "multipart/alternative", "application/*",
        "application/java-vm", "application/x-www-form-urlencoded", "application/x-hdmlc",
        "application/vnd.wap.wmlc", "application/vnd.wap.wmlscriptc",
        "application/vnd.wap.wta-eventc", "application/vnd.wap.uaprof",
        "application/vnd.wap.wtls-ca-certificate", "application/vnd.wap.wtls-user-certificate",
        "application/x-x509-ca-cert", "application/x-x509-user-cert", "image/*", "image/gif",
        "image/jpeg", "image/tiff", "image/png", "image/vnd.wap.wbmp",
        "application/vnd.wap.multipart.*", "application/vnd.wap.multipart.mixed",
        "application/vnd.wap.multipart.form-data", "application/vnd.wap.multipart.byteranges",
        "application/vnd.wap.multipart.alternative", "application/xml", "text/xml",
        "application/vnd.wap.wbxml", "application/x-x968-cross-cert", "application/x-x968-ca-cert",
        "application/x-x968-user-cert", "text/vnd.wap.si", "application/vnd.wap.sic",
        "text/vnd.wap.sl", "application/vnd.wap.slc", "text/vnd.wap.co", "application/vnd.wap.coc",
        "application/vnd.wap.multipart.related", "application/vnd.wap.sia",
        "text/vnd.wap.connectivity-xml", "application/vnd.wap.connectivity-wbxml",
        "application/pkcs7-mime", "application/vnd.wap.hashed-certificate",
        "application/vnd.wap.signed-certificate", "application/vnd.wap.cert-response",
        "application/xhtml+xml", "application/wml+xml", "text/css",
        "application/vnd.wap.mms-message", "application/vnd.wap.rollover-certificate",
    )

    const val CT_MULTIPART_RELATED = "application/vnd.wap.multipart.related"
    const val CT_MULTIPART_MIXED = "application/vnd.wap.multipart.mixed"
    const val CT_SMIL = "application/smil"
    const val CT_TEXT = "text/plain"

    // =============================== Models ===============================

    data class Notification(
        val transactionId: String,
        val contentLocation: String,
        val from: String?,
        val subject: String?,
        val messageSize: Long,
        val expiry: Long,
    )

    data class Part(
        val contentType: String,
        val data: ByteArray,
        val name: String? = null,
        val contentId: String? = null,
        val contentLocation: String? = null,
        val charset: Int = 0,
    ) {
        /** Decodes a text part honouring its charset (UTF-8 when unspecified). */
        fun text(): String = String(data, charsetFor(charset))
        override fun equals(other: Any?) = other is Part && contentType == other.contentType && data.contentEquals(other.data)
        override fun hashCode() = contentType.hashCode() * 31 + data.contentHashCode()
    }

    data class Retrieved(
        val messageId: String?,
        val transactionId: String?,
        val from: String?,
        val to: List<String>,
        val cc: List<String>,
        val dateSeconds: Long,
        val subject: String?,
        val contentType: String,
        val parts: List<Part>,
    )

    data class SendConf(val responseStatus: Int, val messageId: String?, val transactionId: String?)

    // =============================== Parsing ===============================

    /** Parses an M-Notification.ind (the WAP-push `data` extra). */
    fun parseNotification(pdu: ByteArray): Notification? = runCatching {
        val r = Reader(pdu)
        var type = -1
        var tx = ""
        var location = ""
        var from: String? = null
        var subject: String? = null
        var size = 0L
        var expiry = 0L
        while (r.hasMore()) {
            when (val field = r.u8()) {
                H_MESSAGE_TYPE -> type = r.u8()
                H_TRANSACTION_ID -> tx = r.textString()
                H_CONTENT_LOCATION -> location = r.textString()
                H_FROM -> from = r.fromValue()
                H_SUBJECT -> subject = r.encodedString()
                H_MESSAGE_SIZE -> size = r.longInteger()
                H_EXPIRY -> expiry = r.expiry()
                else -> r.skipValue(field)
            }
        }
        if (type != TYPE_NOTIFICATION_IND || location.isBlank()) null
        else Notification(tx, location, from, subject, size, expiry)
    }.getOrNull()

    /** Parses an M-Retrieve.conf (the downloaded message). */
    fun parseRetrieveConf(pdu: ByteArray): Retrieved? = runCatching {
        val r = Reader(pdu)
        var type = -1
        var messageId: String? = null
        var tx: String? = null
        var from: String? = null
        val to = ArrayList<String>()
        val cc = ArrayList<String>()
        var date = 0L
        var subject: String? = null
        var contentType = ""
        var start: String? = null
        while (r.hasMore()) {
            when (val field = r.u8()) {
                H_MESSAGE_TYPE -> type = r.u8()
                H_MESSAGE_ID -> messageId = r.textString()
                H_TRANSACTION_ID -> tx = r.textString()
                H_FROM -> from = r.fromValue()
                H_TO -> to += normalizeAddress(r.encodedString())
                H_CC -> cc += normalizeAddress(r.encodedString())
                H_DATE -> date = r.longInteger()
                H_SUBJECT -> subject = r.encodedString()
                H_CONTENT_TYPE -> {
                    // Content-Type is always the LAST header; the body follows.
                    val ct = r.contentType()
                    contentType = ct.type
                    start = ct.params["start"]
                    break
                }
                else -> r.skipValue(field)
            }
        }
        if (type != TYPE_RETRIEVE_CONF) return@runCatching null
        val parts = if (contentType.startsWith("application/vnd.wap.multipart") || contentType.startsWith("multipart/")) {
            r.multipart()
        } else {
            listOf(Part(contentType, r.rest()))
        }
        Retrieved(messageId, tx, from, to, cc, date, subject, contentType, orderParts(parts, start))
    }.getOrNull()

    /** Parses an M-Send.conf (the SEND result PDU the platform hands back). */
    fun parseSendConf(pdu: ByteArray): SendConf? = runCatching {
        val r = Reader(pdu)
        var type = -1
        var status = -1
        var id: String? = null
        var tx: String? = null
        while (r.hasMore()) {
            when (val field = r.u8()) {
                H_MESSAGE_TYPE -> type = r.u8()
                H_RESPONSE_STATUS -> status = r.u8()
                H_MESSAGE_ID -> id = r.textString()
                H_TRANSACTION_ID -> tx = r.textString()
                else -> r.skipValue(field)
            }
        }
        if (type != TYPE_SEND_CONF) null else SendConf(status, id, tx)
    }.getOrNull()

    // =============================== Encoding ===============================

    /**
     * Encodes an M-Send.req. With more than one part (or any non-text part) it is
     * sent as multipart/related with a SMIL presentation, which is what iPhones,
     * Google Messages and carrier MMSCs expect.
     */
    fun encodeSendReq(
        to: List<String>,
        parts: List<Part>,
        transactionId: String,
        subject: String? = null,
        requestDeliveryReport: Boolean = false,
    ): ByteArray {
        val w = Writer()
        w.u8(H_MESSAGE_TYPE); w.u8(TYPE_SEND_REQ)
        w.u8(H_TRANSACTION_ID); w.textString(transactionId)
        w.u8(H_MMS_VERSION); w.u8(VERSION_1_2)
        w.u8(H_DATE); w.longInteger(System.currentTimeMillis() / 1000)
        // From: insert-address-token — the MMSC fills in our number.
        w.u8(H_FROM); w.u8(1); w.u8(INSERT_ADDRESS)
        to.forEach { w.u8(H_TO); w.encodedString(toMmsAddress(it)) }
        if (!subject.isNullOrBlank()) { w.u8(H_SUBJECT); w.encodedString(subject) }
        w.u8(H_MESSAGE_CLASS); w.u8(0x80) // personal
        w.u8(H_PRIORITY); w.u8(0x81) // normal
        w.u8(H_DELIVERY_REPORT); w.u8(if (requestDeliveryReport) YES else NO)
        w.u8(H_READ_REPORT); w.u8(NO)

        val withSmil = buildPresentation(parts)
        w.u8(H_CONTENT_TYPE)
        val params = Writer()
        params.u8(PARAM_START_V1); params.textString("<smil>")
        params.u8(PARAM_TYPE_REL); params.textString(CT_SMIL)
        val ct = Writer()
        ct.u8(0x80 or WELL_KNOWN_TYPES.indexOf(CT_MULTIPART_RELATED))
        ct.bytes(params.toByteArray())
        w.valueLength(ct.size()); w.bytes(ct.toByteArray())
        w.multipart(withSmil)
        return w.toByteArray()
    }

    /** Encodes an M-NotifyResp.ind telling the MMSC we retrieved [transactionId]. */
    fun encodeNotifyResp(transactionId: String): ByteArray {
        val w = Writer()
        w.u8(H_MESSAGE_TYPE); w.u8(TYPE_NOTIFYRESP_IND)
        w.u8(H_TRANSACTION_ID); w.textString(transactionId)
        w.u8(H_MMS_VERSION); w.u8(VERSION_1_2)
        w.u8(H_STATUS); w.u8(STATUS_RETRIEVED)
        return w.toByteArray()
    }

    /** SMIL first (content-id <smil>), then the media/text parts it references. */
    internal fun buildPresentation(parts: List<Part>): List<Part> {
        val named = parts.mapIndexed { i, p ->
            val name = p.name ?: defaultName(p.contentType, i)
            p.copy(name = name, contentId = "<$name>", contentLocation = name)
        }
        val body = StringBuilder()
        body.append("<smil><head><layout><root-layout/>")
        body.append("<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>")
        body.append("<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/>")
        body.append("</layout></head><body>")
        named.forEach { p ->
            val tag = when {
                p.contentType.startsWith("image/") -> "img"
                p.contentType.startsWith("video/") -> "video"
                p.contentType.startsWith("audio/") -> "audio"
                p.contentType.startsWith("text/plain") -> "text"
                else -> "ref"
            }
            val region = if (tag == "text") " region=\"Text\"" else if (tag == "img" || tag == "video") " region=\"Image\"" else ""
            body.append("<par dur=\"5000ms\"><$tag src=\"${p.name}\"$region/></par>")
        }
        body.append("</body></smil>")
        val smil = Part(CT_SMIL, body.toString().toByteArray(Charsets.UTF_8), name = "smil.xml", contentId = "<smil>", contentLocation = "smil.xml", charset = CHARSET_UTF8)
        return listOf(smil) + named
    }

    private fun defaultName(ct: String, i: Int): String = when {
        ct.startsWith("text/plain") -> "text_$i.txt"
        ct == "image/jpeg" -> "image_$i.jpg"
        ct == "image/png" -> "image_$i.png"
        ct == "image/gif" -> "image_$i.gif"
        ct.startsWith("video/") -> "video_$i.mp4"
        ct.startsWith("audio/") -> "audio_$i.m4a"
        ct.contains("vcard", ignoreCase = true) -> "contact_$i.vcf"
        else -> "file_$i"
    }

    /** "+98912…" → "+98912…/TYPE=PLMN"; e-mail addresses are left alone. */
    internal fun toMmsAddress(address: String): String =
        if (address.contains('@')) address
        else address.filter { it.isDigit() || it == '+' } + "/TYPE=PLMN"

    internal fun normalizeAddress(raw: String): String = raw.substringBefore("/TYPE=").trim()

    /** SMIL-first ordering is for the wire; readers want the start part out of the way. */
    private fun orderParts(parts: List<Part>, start: String?): List<Part> =
        parts.sortedBy { if (start != null && it.contentId == start) 1 else 0 }

    internal fun charsetFor(mib: Int): Charset = runCatching {
        when (mib) {
            0, CHARSET_UTF8 -> Charsets.UTF_8
            3 -> Charsets.US_ASCII
            4 -> Charsets.ISO_8859_1
            1000 -> Charsets.UTF_16BE
            1013 -> Charsets.UTF_16BE
            1014 -> Charsets.UTF_16LE
            1015 -> Charsets.UTF_16
            else -> Charsets.UTF_8
        }
    }.getOrDefault(Charsets.UTF_8)

    // =============================== Reader ===============================

    internal class ContentTypeValue(val type: String, val params: Map<String, String>, val charset: Int)

    internal class Reader(private val buf: ByteArray, private var pos: Int = 0, private val end: Int = buf.size) {
        fun hasMore() = pos < end
        fun u8(): Int {
            if (pos >= end) throw IndexOutOfBoundsException("PDU truncated")
            return buf[pos++].toInt() and 0xFF
        }
        fun peek(): Int = buf[pos].toInt() and 0xFF
        fun bytes(n: Int): ByteArray {
            if (n < 0 || pos + n > end) throw IndexOutOfBoundsException("PDU truncated")
            return buf.copyOfRange(pos, pos + n).also { pos += n }
        }
        fun rest(): ByteArray = bytes(end - pos)
        fun skip(n: Int) { bytes(n) }

        fun uintvar(): Long {
            var v = 0L
            for (i in 0 until 5) {
                val b = u8()
                v = (v shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) return v
            }
            throw IllegalStateException("bad uintvar")
        }

        /** Text-string: optional 0x7F quote, bytes, 0x00. */
        fun textString(): String {
            if (hasMore() && peek() == 0x7F) pos++
            val start = pos
            while (pos < end && buf[pos].toInt() != 0) pos++
            val s = String(buf, start, pos - start, Charsets.UTF_8)
            if (pos < end) pos++ // NUL
            return s
        }

        fun valueLength(): Int {
            val b = u8()
            return when {
                b <= 30 -> b
                b == 31 -> uintvar().toInt()
                else -> throw IllegalStateException("not a value-length: $b")
            }
        }

        fun longInteger(): Long {
            val b = peek()
            if (b >= 0x80) return (u8() and 0x7F).toLong() // short-integer tolerated
            val n = u8()
            var v = 0L
            repeat(n) { v = (v shl 8) or u8().toLong() }
            return v
        }

        fun integerValue(): Long = longInteger()

        /** Encoded-string-value: text, or value-length charset text. */
        fun encodedString(): String {
            val b = peek()
            if (b > 31) return textString()
            val len = valueLength()
            val stop = pos + len
            val charset = integerValue().toInt()
            if (pos < stop && buf[pos].toInt() == 0x7F) pos++
            val start = pos
            var e = start
            while (e < stop && buf[e].toInt() != 0) e++
            val s = String(buf, start, e - start, charsetFor(charset))
            pos = stop
            return s
        }

        /** From: value-length (address-present encoded-string | insert-address). */
        fun fromValue(): String? {
            val len = valueLength()
            val stop = pos + len
            val token = u8()
            val v = if (token == ADDRESS_PRESENT && pos < stop) normalizeAddress(encodedString()) else null
            pos = stop
            return v
        }

        fun expiry(): Long {
            val len = valueLength()
            val stop = pos + len
            u8() // absolute/relative token
            val v = longInteger()
            pos = stop
            return v
        }

        /** Content-type: constrained media, or value-length media-type *(parameter). */
        fun contentType(): ContentTypeValue {
            val b = peek()
            if (b >= 0x80) return ContentTypeValue(wellKnown(u8() and 0x7F), emptyMap(), 0)
            if (b > 31) return ContentTypeValue(textString(), emptyMap(), 0)
            val len = valueLength()
            val stop = pos + len
            val type = if (peek() >= 0x80) wellKnown(u8() and 0x7F) else if (peek() > 31) textString() else {
                // Long-integer well-known type (rare).
                wellKnown(longInteger().toInt())
            }
            val params = HashMap<String, String>()
            var charset = 0
            while (pos < stop) {
                val p = peek()
                if (p >= 0x80) {
                    u8()
                    when (p) {
                        PARAM_CHARSET -> charset = if (peek() >= 0x80) u8() and 0x7F else longInteger().toInt()
                        PARAM_TYPE_REL -> params["type"] = if (peek() >= 0x80) wellKnown(u8() and 0x7F) else textString()
                        PARAM_START_V1, PARAM_START -> params["start"] = textString()
                        PARAM_NAME_V1, PARAM_NAME -> params["name"] = textString()
                        PARAM_FILENAME_V1, PARAM_FILENAME -> params["filename"] = textString()
                        else -> skipParamValue()
                    }
                } else {
                    // Untyped parameter: token-text then value.
                    val key = textString().lowercase()
                    val value = if (peek() >= 0x80) (u8() and 0x7F).toString() else textString()
                    params[key] = value
                }
            }
            pos = stop
            return ContentTypeValue(type, params, charset)
        }

        private fun skipParamValue() {
            val b = peek()
            when {
                b >= 0x80 -> u8()
                b <= 30 -> { val n = u8(); skip(n) }
                b == 31 -> { u8(); skip(uintvar().toInt()) }
                else -> textString()
            }
        }

        /** Skips an unknown header's value using the generic WSP value rules. */
        fun skipValue(@Suppress("UNUSED_PARAMETER") field: Int) {
            if (!hasMore()) return
            val b = peek()
            when {
                b >= 0x80 -> u8()
                b <= 30 -> { val n = u8(); skip(n) }
                b == 31 -> { u8(); skip(uintvar().toInt()) }
                else -> textString()
            }
        }

        fun multipart(): List<Part> {
            val count = uintvar().toInt()
            val parts = ArrayList<Part>(count)
            repeat(count) {
                val headersLen = uintvar().toInt()
                val dataLen = uintvar().toInt()
                val headersEnd = pos + headersLen
                val h = Reader(buf, pos, headersEnd)
                val ct = h.contentType()
                var contentId: String? = null
                var location: String? = null
                while (h.hasMore()) {
                    val field = h.peek()
                    if (field >= 0x80) {
                        h.u8()
                        when (field) {
                            P_CONTENT_ID -> contentId = h.textStringQuoted()
                            P_CONTENT_LOCATION -> location = h.textString()
                            else -> h.skipValue(field)
                        }
                    } else {
                        // Application header: token-text name, then a text value.
                        h.textString()
                        if (h.hasMore()) h.textString()
                    }
                }
                pos = headersEnd
                val data = bytes(dataLen)
                parts += Part(
                    contentType = ct.type,
                    data = data,
                    name = ct.params["name"] ?: ct.params["filename"] ?: location,
                    contentId = contentId,
                    contentLocation = location,
                    charset = ct.charset,
                )
            }
            return parts
        }

        /** Quoted-string (0x22 prefix) or plain text-string. */
        fun textStringQuoted(): String {
            if (hasMore() && peek() == 0x22) pos++
            return textString()
        }

        private fun wellKnown(code: Int): String = WELL_KNOWN_TYPES.getOrNull(code) ?: "application/octet-stream"
    }

    // =============================== Writer ===============================

    internal class Writer {
        private val out = ByteArrayOutputStream()
        fun size() = out.size()
        fun toByteArray(): ByteArray = out.toByteArray()
        fun u8(v: Int) = out.write(v and 0xFF)
        fun bytes(b: ByteArray) = out.write(b)

        fun uintvar(value: Long) {
            var v = value
            val tmp = ArrayList<Int>()
            tmp += (v and 0x7F).toInt()
            v = v ushr 7
            while (v > 0) {
                tmp += ((v and 0x7F) or 0x80).toInt()
                v = v ushr 7
            }
            tmp.asReversed().forEach(::u8)
        }

        fun textString(s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            if (b.isNotEmpty() && (b[0].toInt() and 0xFF) >= 0x80) u8(0x7F)
            bytes(b); u8(0)
        }

        fun quotedString(s: String) { u8(0x22); bytes(s.toByteArray(Charsets.UTF_8)); u8(0) }

        fun valueLength(len: Int) {
            if (len <= 30) u8(len) else { u8(31); uintvar(len.toLong()) }
        }

        fun longInteger(v: Long) {
            val bytes = ArrayList<Int>()
            var x = v
            do { bytes += (x and 0xFF).toInt(); x = x ushr 8 } while (x > 0)
            u8(bytes.size)
            bytes.asReversed().forEach(::u8)
        }

        /** Encoded-string-value; non-ASCII gets an explicit UTF-8 charset. */
        fun encodedString(s: String) {
            if (s.all { it.code < 0x80 }) { textString(s); return }
            val inner = Writer()
            inner.u8(0x80 or CHARSET_UTF8.coerceAtMost(0x7F)) // 106 fits a short-integer
            inner.textString(s)
            valueLength(inner.size()); bytes(inner.toByteArray())
        }

        fun multipart(parts: List<Part>) {
            uintvar(parts.size.toLong())
            parts.forEach { p ->
                val headers = Writer()
                // Content-type (general form with charset/name when useful).
                val ct = Writer()
                val code = WELL_KNOWN_TYPES.indexOf(p.contentType)
                if (code in 0..0x7F) ct.u8(0x80 or code) else ct.textString(p.contentType)
                if (p.contentType.startsWith("text/") || p.contentType == CT_SMIL) {
                    ct.u8(PARAM_CHARSET); ct.u8(0x80 or CHARSET_UTF8)
                }
                if (p.name != null) { ct.u8(PARAM_NAME_V1); ct.textString(p.name) }
                headers.valueLength(ct.size()); headers.bytes(ct.toByteArray())
                if (p.contentId != null) { headers.u8(P_CONTENT_ID); headers.quotedString(p.contentId) }
                if (p.contentLocation != null) { headers.u8(P_CONTENT_LOCATION); headers.textString(p.contentLocation) }
                uintvar(headers.size().toLong())
                uintvar(p.data.size.toLong())
                bytes(headers.toByteArray())
                bytes(p.data)
            }
        }
    }
}
