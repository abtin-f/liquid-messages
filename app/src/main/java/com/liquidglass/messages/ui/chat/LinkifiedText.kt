package com.liquidglass.messages.ui.chat

import android.content.ActivityNotFoundException
import com.liquidglass.messages.ui.components.IosDialogs
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

/** A detected link: its character range in the text and the uri it opens. */
internal data class DetectedLink(val start: Int, val end: Int, val url: String)

/**
 * Finds web links, e-mail addresses and phone numbers with the platform's
 * [Linkify] (the detector system apps use; phone matching is region-aware, so
 * Iranian numbers like 0912… are found too).
 */
internal fun detectLinks(text: String): List<DetectedLink> {
    if (text.isBlank()) return emptyList()
    val spannable = SpannableString(text)
    runCatching { Linkify.addLinks(spannable, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES) }
    val links = spannable.getSpans(0, spannable.length, URLSpan::class.java)
        .map { DetectedLink(spannable.getSpanStart(it), spannable.getSpanEnd(it), it.url) }
        .toMutableList()

    // Phone numbers: Linkify's matcher uses the *locale's* region, so on an
    // English-language phone it misses local formats like 0912 123 4567. This
    // region-free rule accepts 8–15 digits with optional +, spaces and dashes.
    PhoneRegex.findAll(text).forEach { m ->
        val digits = m.value.count(Char::isDigit)
        val overlaps = links.any { m.range.first < it.end && m.range.last >= it.start }
        // 10+ digits, or a local/international prefix (0…, +…); plain 8–9 digit
        // runs are usually verification codes, not numbers to call.
        val looksLikePhone = digits in 10..15 || (digits >= 8 && (m.value.startsWith("0") || m.value.startsWith("+")))
        if (looksLikePhone && digits <= 15 && !overlaps) {
            links += DetectedLink(m.range.first, m.range.last + 1, "tel:" + m.value.filter { it.isDigit() || it == '+' })
        }
    }
    return links.sortedBy { it.start }
}

private val PhoneRegex = Regex("""(?<![\w@/=.+])\+?\d[\d\- ]{6,17}\d(?![\w@])""")

/**
 * The message body with tappable, underlined links. In a grey (received)
 * bubble links are iOS blue; in a coloured (sent) bubble they stay white so
 * they remain readable on blue/green.
 */
@Composable
internal fun rememberLinkifiedText(text: String, linkColor: Color): AnnotatedString {
    val context = LocalContext.current
    return remember(text, linkColor) {
        val links = detectLinks(text)
        if (links.isEmpty()) return@remember AnnotatedString(text)
        val styles = TextLinkStyles(
            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            pressedStyle = SpanStyle(color = linkColor.copy(alpha = 0.6f), textDecoration = TextDecoration.Underline),
        )
        buildAnnotatedString {
            append(text)
            links.forEach { link ->
                addLink(
                    LinkAnnotation.Url(link.url, styles) { open(context, link.url) },
                    link.start,
                    link.end,
                )
            }
        }
    }
}

/** Opens a link with whichever app handles it; never crashes when none does. */
private fun open(context: Context, url: String) {
    val intent = when {
        url.startsWith("tel:") -> Intent(Intent.ACTION_DIAL, Uri.parse(url))
        url.startsWith("mailto:") -> Intent(Intent.ACTION_SENDTO, Uri.parse(url))
        else -> Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        IosDialogs.alert("Can't Open Link", "No app on this phone can open this link.")
    } catch (_: SecurityException) {
        IosDialogs.alert("Can't Open Link", "No app on this phone can open this link.")
    }
}
