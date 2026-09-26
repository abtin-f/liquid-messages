package com.liquidglass.messages.ui.chat

import android.content.Context
import com.liquidglass.messages.ui.components.IosDialogs
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.liquidglass.messages.data.model.Attachment
import com.liquidglass.messages.ui.components.GlassCircleButton
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import java.io.File

/**
 * MMS attachments above a message's text, iOS-style: photos as rounded images
 * with no bubble or tail, videos as a dark tile with a play glyph, everything
 * else (contact cards, audio, documents) as a compact file bubble.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentStack(
    attachments: List<Attachment>,
    outgoing: Boolean,
    onOpenImage: (Attachment) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier,
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        attachments.forEach { a ->
            val click = Modifier.combinedClickable(
                onClick = { if (a.isImage) onOpenImage(a) else openExternally(context, a) },
                onLongClick = onLongPress,
            )
            when {
                a.isImage -> {
                    // Fixed card while decoding (no size is known yet), then the
                    // photo's own aspect ratio within iOS's max bounds.
                    var loaded by remember(a.uri) { mutableStateOf(false) }
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(Uri.parse(a.uri)).crossfade(true).build(),
                        contentDescription = a.name ?: "Photo",
                        contentScale = ContentScale.Fit,
                        onState = { loaded = it is AsyncImagePainter.State.Success },
                        modifier = Modifier
                            .then(
                                if (loaded) Modifier.widthIn(min = 80.dp, max = 250.dp).heightIn(min = 60.dp, max = 340.dp)
                                else Modifier.size(width = 200.dp, height = 150.dp),
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .background(LiquidTheme.colors.receivedBubble)
                            .then(click),
                    )
                }
                a.isVideo -> Box(
                    modifier = Modifier
                        .size(width = 220.dp, height = 150.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1C1C1E))
                        .then(click),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("▶", color = Color.Black, style = IosType.title2) }
                }
                else -> FileChip(a, outgoing, click)
            }
        }
    }
}

@Composable
private fun FileChip(a: Attachment, outgoing: Boolean, click: Modifier) {
    val colors = LiquidTheme.colors
    val bg = if (outgoing) colors.sentBubbleBottom else colors.receivedBubble
    val fg = if (outgoing) Color.White else colors.primaryText
    Row(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(click)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (a.isContactCard) IosIcons.PersonCircle else IosIcons.Doc,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                a.name ?: if (a.isContactCard) "Contact" else "Attachment",
                style = IosType.subheadline,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(kindOf(a.mimeType), style = IosType.caption1, color = fg.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

/** Full-screen photo viewer (tap or back to close). */
@Composable
fun ImageViewer(attachment: Attachment, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        AsyncImage(
            model = Uri.parse(attachment.uri),
            contentDescription = attachment.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier.statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassCircleButton(icon = IosIcons.Close, contentDescription = "Close", onClick = onClose, iconSize = 16.dp)
            GlassCircleButton(icon = IosIcons.Link, contentDescription = "Share", onClick = { share(context, attachment) }, iconSize = 18.dp)
        }
    }
}

/**
 * Other apps can't read content://mms/part/…, so the part is copied into our
 * cache and handed over through our FileProvider with a read grant.
 */
private fun exportable(context: Context, a: Attachment): Uri? = runCatching {
    val src = Uri.parse(a.uri)
    if (src.scheme == "file") {
        return@runCatching FileProvider.getUriForFile(context, "${context.packageName}.files", File(src.path!!))
    }
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(a.mimeType) ?: "bin"
    val name = (a.name?.takeIf { it.contains('.') } ?: "attachment.$ext").replace(Regex("[^A-Za-z0-9._-]"), "_")
    val out = File(dir, name)
    context.contentResolver.openInputStream(src)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
    FileProvider.getUriForFile(context, "${context.packageName}.files", out)
}.getOrNull()

fun openExternally(context: Context, a: Attachment) {
    val uri = exportable(context, a)
    if (uri == null) {
        IosDialogs.alert("Can't Open Attachment", "This attachment couldn't be opened.")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { IosDialogs.alert("No App Available", "No app on this phone can open ${a.mimeType} files.") }
}

private fun share(context: Context, a: Attachment) {
    val uri = exportable(context, a) ?: return
    val send = Intent(Intent.ACTION_SEND).setType(a.mimeType).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Finder-style kind names ("PDF Document") instead of raw MIME types. */
internal fun kindOf(mime: String): String = when {
    mime == "application/pdf" -> "PDF Document"
    mime.contains("vcard", true) -> "Contact Card"
    mime.startsWith("audio/") -> "Audio"
    mime.startsWith("video/") -> "Video"
    mime.startsWith("image/") -> "Image"
    mime.startsWith("text/") -> "Text Document"
    mime.contains("zip") -> "ZIP Archive"
    mime.contains("word") -> "Word Document"
    mime.contains("sheet") || mime.contains("excel") -> "Spreadsheet"
    mime.contains("presentation") || mime.contains("powerpoint") -> "Presentation"
    else -> "Document"
}
