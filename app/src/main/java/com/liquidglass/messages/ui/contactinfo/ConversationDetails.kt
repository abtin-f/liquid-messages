package com.liquidglass.messages.ui.contactinfo

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.liquidglass.messages.data.local.ChatWallpaper
import com.liquidglass.messages.data.location.LocationLink
import com.liquidglass.messages.data.location.SharedLocation
import com.liquidglass.messages.data.model.Attachment
import com.liquidglass.messages.data.model.Message
import com.liquidglass.messages.data.model.MessageText
import com.liquidglass.messages.ui.chat.LocationCard
import com.liquidglass.messages.ui.chat.openExternally
import com.liquidglass.messages.ui.components.IosGroupedPage
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.components.IosRow
import com.liquidglass.messages.ui.components.iosSection
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.ui.theme.fontFamilyFor
import com.liquidglass.messages.ui.theme.wallpaperBrush
import java.text.DateFormat
import java.util.Date

/** Everything shared in a conversation, sorted newest first (iOS 27 details). */
data class SharedContent(
    val photos: List<Attachment>,
    val links: List<String>,
    val documents: List<Pair<Attachment, Long>>,
    val locations: List<Pair<SharedLocation, Long>>,
) {
    companion object {
        private val UrlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"]+[^\s<>".,;:!?)\]])""")

        fun from(messages: List<Message>): SharedContent {
            val newest = messages.asReversed()
            val photos = newest.flatMap { m -> m.attachments.filter { it.isImage || it.isVideo } }
            val docs = newest.flatMap { m -> m.attachments.filterNot { it.isImage || it.isVideo }.map { it to m.timestamp } }
            val locations = newest.mapNotNull { m ->
                LocationLink.parse(MessageText.visible(m.body))?.let { it to m.timestamp }
            }
            val links = newest
                .filter { LocationLink.parse(MessageText.visible(it.body)) == null }
                .flatMap { m -> UrlRegex.findAll(m.body).map { it.value }.toList() }
                .map { if (it.startsWith("www.", ignoreCase = true)) "https://$it" else it }
                .distinct()
            return SharedContent(photos, links, docs, locations)
        }
    }
}

/** Sub-pages of Conversation Details. */
enum class DetailPage { MAIN, PHOTOS, LINKS, DOCUMENTS, LOCATIONS, SEARCH, BACKGROUND }

private val TileBlue = Color(0xFF007AFF)
private val TileGreen = Color(0xFF34C759)
private val TileOrange = Color(0xFFFF9500)
private val TileRed = Color(0xFFFF3B30)

/**
 * iOS 27 shared-content categories: a 2-column grid of cards with an icon,
 * name and count. Each opens a real page listing that content.
 */
@Composable
fun SharedCategoryGrid(content: SharedContent, open: (DetailPage) -> Unit) {
    val cards = listOf(
        Triple(DetailPage.PHOTOS, "Photos", content.photos.size),
        Triple(DetailPage.LINKS, "Links", content.links.size),
        Triple(DetailPage.DOCUMENTS, "Documents", content.documents.size),
        Triple(DetailPage.LOCATIONS, "Locations", content.locations.size),
    )
    val icons = mapOf(
        DetailPage.PHOTOS to (IosIcons.Photos to TileBlue),
        DetailPage.LINKS to (IosIcons.Link to TileGreen),
        DetailPage.DOCUMENTS to (IosIcons.Doc to TileOrange),
        DetailPage.LOCATIONS to (IosIcons.Location to TileRed),
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (page, title, count) ->
                    val (icon, tint) = icons.getValue(page)
                    CategoryCard(title, count, icon, tint, Modifier.weight(1f)) { open(page) }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(title: String, count: Int, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(colors.groupedCell)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(title, style = IosType.headline, color = colors.primaryText)
        Text(if (count == 0) "None" else count.toString(), style = IosType.subheadline, color = colors.secondaryText)
    }
}

/** "Shared with You"-style strip of the newest photos, with See All. */
@Composable
fun RecentPhotosStrip(photos: List<Attachment>, onOpen: (Attachment) -> Unit, onSeeAll: () -> Unit) {
    val colors = LiquidTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Photos", style = IosType.title2.copy(fontSize = IosType.headline.fontSize * 1.15f), color = colors.primaryText, modifier = Modifier.weight(1f))
            Text("See All", style = IosType.body, color = colors.accent, modifier = Modifier.clickable(onClick = onSeeAll))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            photos.take(12).forEach { a -> Thumb(a, Modifier.size(104.dp).clip(RoundedCornerShape(14.dp))) { onOpen(a) } }
        }
    }
}

@Composable
internal fun Thumb(a: Attachment, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(modifier.background(LiquidTheme.colors.receivedBubble).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (a.isImage) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(Uri.parse(a.uri)).crossfade(true).build(),
                contentDescription = a.name ?: "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E)), contentAlignment = Alignment.Center) {
                Text("▶", color = Color.White, style = IosType.title2)
            }
        }
    }
}

/* ------------------------------ Detail pages ------------------------------ */

@Composable
fun PhotosPage(photos: List<Attachment>, onBack: () -> Unit, onOpen: (Attachment) -> Unit) {
    IosGroupedPage(title = "Photos", onBack = onBack) {
        if (photos.isEmpty()) emptyNote("No Photos", "Photos and videos you share in this conversation appear here.")
        else item(key = "grid") {
            Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                photos.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { a -> Thumb(a, Modifier.weight(1f).aspectRatio(1f)) { onOpen(a) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun LinksPage(links: List<String>, onBack: () -> Unit, onOpen: (String) -> Unit) {
    IosGroupedPage(title = "Links", onBack = onBack) {
        if (links.isEmpty()) emptyNote("No Links", "Links shared in this conversation appear here.")
        else iosSection("links") {
            links.forEachIndexed { i, url ->
                IosRow(
                    title = Uri.parse(url).host?.removePrefix("www.") ?: url,
                    subtitle = url,
                    icon = IosIcons.Link,
                    iconBackground = TileGreen,
                    chevron = true,
                    showDivider = i < links.lastIndex,
                    onClick = { onOpen(url) },
                )
            }
        }
    }
}

@Composable
fun DocumentsPage(docs: List<Pair<Attachment, Long>>, onBack: () -> Unit) {
    val context = LocalContext.current
    IosGroupedPage(title = "Documents", onBack = onBack) {
        if (docs.isEmpty()) emptyNote("No Documents", "Files, contact cards and audio shared here appear in this list.")
        else iosSection("docs") {
            docs.forEachIndexed { i, (a, at) ->
                val size = if (a.sizeBytes > 0) android.text.format.Formatter.formatShortFileSize(context, a.sizeBytes) + " · " else ""
                IosRow(
                    title = a.name ?: a.mimeType.substringAfter('/').uppercase(),
                    subtitle = size + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at)),
                    icon = IosIcons.Doc,
                    iconBackground = TileOrange,
                    chevron = true,
                    showDivider = i < docs.lastIndex,
                    onClick = { openExternally(context, a) },
                )
            }
        }
    }
}

@Composable
fun LocationsPage(locations: List<Pair<SharedLocation, Long>>, onBack: () -> Unit) {
    val colors = LiquidTheme.colors
    IosGroupedPage(title = "Locations", onBack = onBack) {
        if (locations.isEmpty()) emptyNote("No Locations", "Locations shared in this conversation appear here.")
        else locations.forEachIndexed { i, (loc, at) ->
            item(key = "loc$i") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LocationCard(location = loc, outgoing = false, onLongPress = {})
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at)),
                        style = IosType.footnote,
                        color = colors.secondaryText,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                }
            }
        }
    }
}

/** iOS "Search in Conversation": a search field and matching messages with the match highlighted. */
@Composable
fun SearchPage(messages: List<Message>, contactName: String, onBack: () -> Unit) {
    val colors = LiquidTheme.colors
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val results = remember(q, messages) {
        if (q.isEmpty()) emptyList()
        else messages.asReversed().filter { MessageText.visible(it.body).contains(q, ignoreCase = true) }
    }
    IosGroupedPage(title = "Search", onBack = onBack) {
        item(key = "field") {
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.fieldBackground)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(IosIcons.Search, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = IosType.body.copy(color = colors.primaryText, textDirection = TextDirection.Content),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Search in Conversation", style = IosType.body, color = colors.secondaryText)
                            inner()
                        },
                    )
                }
            }
        }
        when {
            q.isEmpty() -> emptyNote("Search Messages", "Find words, links and numbers in this conversation.")
            results.isEmpty() -> emptyNote("No Results", "Nothing matches “$q”.")
            else -> iosSection("results", header = "${results.size} Messages") {
                results.take(200).forEachIndexed { i, m ->
                    val text = MessageText.visible(m.body)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row {
                            Text(
                                if (m.isOutgoing) "You" else contactName,
                                style = IosType.subheadline,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primaryText,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(m.timestamp)),
                                style = IosType.footnote,
                                color = colors.secondaryText,
                            )
                        }
                        Text(
                            highlight(text, q, colors.accent),
                            style = IosType.subheadline,
                            fontFamily = fontFamilyFor(text),
                            color = colors.secondaryText,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (i < minOf(results.size, 200) - 1) {
                        Box(Modifier.padding(start = 20.dp).fillMaxWidth().height(0.5.dp).background(colors.divider))
                    }
                }
            }
        }
    }
}

private fun highlight(text: String, q: String, color: Color) = buildAnnotatedString {
    var from = 0
    while (true) {
        val at = text.indexOf(q, from, ignoreCase = true)
        if (at < 0) {
            append(text.substring(from)); break
        }
        append(text.substring(from, at))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(text.substring(at, at + q.length)) }
        from = at + q.length
    }
}

/** Per-conversation background, with "Default" following Settings. */
@Composable
fun BackgroundPage(current: ChatWallpaper?, onPick: (ChatWallpaper?) -> Unit, onBack: () -> Unit) {
    val colors = LiquidTheme.colors
    IosGroupedPage(title = "Background", onBack = onBack) {
        iosSection("bg", footer = "Only this conversation uses this background. Default follows Settings › Personalization.") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val options: List<ChatWallpaper?> = listOf(null) + ChatWallpaper.entries
                options.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { w ->
                            val selected = w == current
                            val shape = RoundedCornerShape(14.dp)
                            val brush = w?.let { wallpaperBrush(it, colors.isDark) }
                            Column(Modifier.weight(1f).clickable { onPick(w) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.72f)
                                        .clip(shape)
                                        .background(colors.chatBackground)
                                        .then(if (brush != null) Modifier.background(brush) else Modifier)
                                        .border(if (selected) BorderStroke(2.5.dp, colors.accent) else BorderStroke(0.5.dp, colors.divider), shape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (w == null) Text("Aa", style = IosType.headline, color = colors.secondaryText)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    w?.label ?: "Default",
                                    style = IosType.footnote,
                                    color = if (selected) colors.accent else colors.secondaryText,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.emptyNote(title: String, body: String) {
    item(key = "empty") {
        val colors = LiquidTheme.colors
        Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = IosType.title2, color = colors.primaryText)
            Spacer(Modifier.height(6.dp))
            Text(body, style = IosType.subheadline, color = colors.secondaryText, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
