package com.liquidglass.messages.ui.chat

import android.content.Context
import com.liquidglass.messages.ui.components.IosDialogs
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidglass.messages.data.location.SharedLocation
import com.liquidglass.messages.data.location.StaticMap
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val CardWidth = 250.dp
private val MapHeight = 150.dp

/**
 * iMessage-style location card: a map snapshot with a pin in the middle and a
 * footer naming the place. Tap opens the phone's maps app (Google Maps, Neshan,
 * Balad… whichever handles `geo:`), long-press opens the message menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocationCard(
    location: SharedLocation,
    outgoing: Boolean,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LiquidTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val wPx = with(density) { CardWidth.roundToPx() }
    val hPx = with(density) { MapHeight.roundToPx() }

    val map by produceState<ImageBitmap?>(null, location.latitude, location.longitude, wPx, hPx) {
        value = withContext(Dispatchers.IO) {
            StaticMap.render(context, location.latitude, location.longitude, wPx, hPx)?.asImageBitmap()
        }
    }
    val address by produceState<String?>(null, location.latitude, location.longitude) {
        value = withContext(Dispatchers.IO) { StaticMap.address(context, location.latitude, location.longitude) }
    }

    val footerBg = if (outgoing) colors.sentBubbleBottom else colors.receivedBubble
    val footerFg = if (outgoing) Color.White else colors.primaryText
    val footerSub = if (outgoing) Color.White.copy(alpha = 0.8f) else colors.secondaryText

    Column(
        modifier = modifier
            .width(CardWidth)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = { openInMaps(context, location) },
                onLongClick = onLongPress,
            ),
    ) {
        Box(Modifier.fillMaxWidth().height(MapHeight)) {
            val tiles = map
            if (tiles != null) {
                Image(
                    bitmap = tiles,
                    contentDescription = "Map",
                    contentScale = ContentScale.Crop,
                    // Dark mode: dim the light OSM style so the card doesn't glare.
                    colorFilter = if (colors.isDark) ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.72f, 0.72f, 0.78f, 1f) }) else null,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    "© OpenStreetMap",
                    fontSize = 8.sp,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(topStart = 4.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            } else {
                MapPlaceholder(dark = colors.isDark)
            }
            Pin(Modifier.align(Alignment.Center))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(footerBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(IosIcons.Location, contentDescription = null, tint = footerFg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    location.label ?: "Location",
                    style = IosType.subheadline,
                    fontWeight = FontWeight.SemiBold,
                    color = footerFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    address ?: String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude),
                    style = IosType.caption1,
                    color = footerSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(IosIcons.ChevronRight, contentDescription = null, tint = footerSub, modifier = Modifier.size(12.dp))
        }
    }
}

/** The iOS map pin: red head with a white ring and soft shadow, on a short stem. */
@Composable
private fun Pin(modifier: Modifier) {
    Canvas(modifier.size(width = 30.dp, height = 44.dp)) {
        val cx = size.width / 2
        val head = size.width * 0.36f
        val headY = head + 2.dp.toPx()
        // Stem ends exactly at the canvas centre (the geographic point).
        drawLine(Color(0xFF8E8E93), Offset(cx, headY), Offset(cx, size.height / 2), strokeWidth = 2.dp.toPx())
        drawCircle(Color.Black.copy(alpha = 0.18f), radius = 3.dp.toPx(), center = Offset(cx, size.height / 2))
        drawCircle(Color.Black.copy(alpha = 0.22f), radius = head + 1.5.dp.toPx(), center = Offset(cx, headY + 1.dp.toPx()))
        drawCircle(Color.White, radius = head + 1.5.dp.toPx(), center = Offset(cx, headY))
        drawCircle(Color(0xFFFF3B30), radius = head, center = Offset(cx, headY))
    }
}

/** Offline stand-in: a soft street grid so the card still reads as a map. */
@Composable
private fun MapPlaceholder(dark: Boolean) {
    val land = if (dark) Color(0xFF2C2C2E) else Color(0xFFF1EEE6)
    val road = if (dark) Color(0xFF48484A) else Color.White
    val park = if (dark) Color(0xFF28382A) else Color(0xFFD7E8C8)
    val water = if (dark) Color(0xFF1F2F40) else Color(0xFFBBD7EE)
    Canvas(Modifier.fillMaxSize().background(land)) {
        drawRect(park, topLeft = Offset(size.width * 0.06f, size.height * 0.1f), size = androidx.compose.ui.geometry.Size(size.width * 0.22f, size.height * 0.3f))
        drawRect(water, topLeft = Offset(size.width * 0.7f, size.height * 0.62f), size = androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height * 0.38f))
        val w = 5.dp.toPx()
        listOf(0.18f, 0.52f, 0.83f).forEach { f -> drawLine(road, Offset(0f, size.height * f), Offset(size.width, size.height * f + 6f), strokeWidth = w) }
        listOf(0.12f, 0.4f, 0.66f, 0.92f).forEach { f -> drawLine(road, Offset(size.width * f, 0f), Offset(size.width * f - 10f, size.height), strokeWidth = w) }
        drawLine(road, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = w * 1.6f)
        drawRect(Color.Black.copy(alpha = 0.04f), style = Stroke(1f))
    }
}

private fun openInMaps(context: Context, loc: SharedLocation) {
    val lat = String.format(Locale.US, "%.6f", loc.latitude)
    val lng = String.format(Locale.US, "%.6f", loc.longitude)
    val label = Uri.encode(loc.label ?: "Location")
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=17/$lat/$lng"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(geo) }
        .recoverCatching { context.startActivity(web) }
        .onFailure { IosDialogs.alert("No Maps App", "Install a maps app to open locations.") }
}
