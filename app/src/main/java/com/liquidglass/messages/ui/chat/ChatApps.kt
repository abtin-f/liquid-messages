package com.liquidglass.messages.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.liquidglass.messages.data.schedule.ScheduledMessage
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.components.glassControl
import com.liquidglass.messages.ui.glass.GlassStyle
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.util.CurrentLocation
import com.liquidglass.messages.util.TimeFormat
import java.util.Calendar
import java.util.Locale

/** Items of the iOS 26 "+" apps menu. */
enum class ChatApp(val label: String, val color: Color) {
    CAMERA("Camera", Color(0xFF8E8E93)),
    PHOTOS("Photos", Color(0xFFFF9500)),
    STICKERS("Stickers", Color(0xFFFF2D55)),
    FILES("Files", Color(0xFF007AFF)),
    LOCATION("Location", Color(0xFF34C759)),
    CONTACT("Contact", Color(0xFF5856D6)),
    SEND_LATER("Send Later", Color(0xFF5AC8FA)),
    EFFECTS("Effects", Color(0xFFAF52DE)),
    ;

    val icon: ImageVector
        get() = when (this) {
            CAMERA -> IosIcons.Camera
            PHOTOS -> IosIcons.Photos
            STICKERS -> IosIcons.Smile
            FILES -> IosIcons.Doc
            LOCATION -> IosIcons.Location
            CONTACT -> IosIcons.PersonCircle
            SEND_LATER -> IosIcons.Clock
            EFFECTS -> IosIcons.Sparkles
        }
}

/** The vertical Liquid Glass list iOS 26 opens from the composer's "+". */
@Composable
fun ChatAppsMenu(onPick: (ChatApp) -> Unit, modifier: Modifier = Modifier) {
    val colors = LiquidTheme.colors
    Column(
        modifier = modifier
            .width(230.dp)
            .glassControl(RoundedCornerShape(28.dp), colors.glassShadow, colors, GlassStyle.Menu)
            .padding(vertical = 8.dp),
    ) {
        ChatApp.entries.forEach { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(app) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(app.color.copy(alpha = 0.85f), app.color))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(app.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(app.label, style = IosType.body, color = colors.primaryText)
            }
        }
    }
}

/**
 * iOS 18 "Send Later" chooser: quick picks plus a custom date & time.
 * Returns the chosen epoch-millis through [onPick].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendLaterSheet(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val colors = LiquidTheme.colors
    val context = LocalContext.current
    var customStep by remember { mutableStateOf(0) } // 0 quick, 1 date, 2 time
    var pickedDay by remember { mutableStateOf<Long?>(null) }

    val quick = remember {
        val now = Calendar.getInstance()
        buildList {
            add("In 1 Hour" to now.timeInMillis + 60 * 60_000L)
            val tonight = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
            if (tonight.after(now)) add("Tonight" to tonight.timeInMillis)
            val tomorrow = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            add("Tomorrow Morning" to tomorrow.timeInMillis)
        }
    }

    when (customStep) {
        0 -> ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.groupedBackground,
        ) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text("Send Later", style = IosType.title2, color = colors.primaryText, modifier = Modifier.padding(start = 4.dp, bottom = 14.dp))
                Column(Modifier.clip(RoundedCornerShape(22.dp)).background(colors.groupedCell)) {
                    quick.forEach { (label, at) ->
                        SheetRow(label, TimeFormat.sendLaterLabel(context, at), divider = true) { onPick(at) }
                    }
                    SheetRow("Custom…", null, divider = false) { customStep = 1 }
                }
            }
        }
        1 -> {
            val dateState = rememberDatePickerState(
                initialSelectedDateMillis = System.currentTimeMillis(),
                selectableDates = object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) =
                        utcTimeMillis >= System.currentTimeMillis() - 86_400_000L
                },
            )
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = {
                        pickedDay = dateState.selectedDateMillis
                        customStep = 2
                    }) { Text("Next", color = colors.accent) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.accent) } },
            ) { DatePicker(state = dateState) }
        }
        else -> {
            val now = Calendar.getInstance()
            val timeState = rememberTimePickerState(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE) + 5)
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = colors.groupedCell,
                title = { Text("Choose a Time", style = IosType.headline) },
                text = { TimePicker(state = timeState) },
                confirmButton = {
                    TextButton(onClick = {
                        // DatePicker returns UTC midnight; rebuild in local time.
                        val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = pickedDay ?: System.currentTimeMillis()
                        }
                        val at = Calendar.getInstance().apply {
                            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), timeState.hour, timeState.minute, 0)
                        }.timeInMillis
                        if (at <= System.currentTimeMillis()) {
                            Toast.makeText(context, "Pick a time in the future.", Toast.LENGTH_SHORT).show()
                        } else {
                            onPick(at)
                        }
                    }) { Text("Done", color = colors.accent, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.accent) } },
            )
        }
    }
}

@Composable
private fun SheetRow(title: String, value: String?, divider: Boolean, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    Box {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = IosType.body, color = colors.primaryText, modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = IosType.subheadline, color = colors.secondaryText)
        }
        if (divider) {
            Box(Modifier.align(Alignment.BottomStart).padding(start = 20.dp).fillMaxWidth().height(0.5.dp).background(colors.divider))
        }
    }
}

private val StickerSet = listOf(
    "😂", "🥹", "😍", "🥰", "😘", "😎", "🤩", "🥳", "😭", "😤", "🤯", "😱",
    "🤔", "🫡", "🙄", "😴", "🤗", "🫶", "👍", "👏", "🙏", "💪", "🔥", "✨",
    "❤️", "💔", "💯", "🎉", "🎂", "🌹", "☕", "🍕", "⚽", "🏆", "🚀", "🌙",
)

/** Sticker drawer: tapping one sends it straight away as a jumbo emoji. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerSheet(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LiquidTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.groupedBackground) {
        Text("Stickers", style = IosType.title2, color = colors.primaryText, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 12.dp),
        ) {
            items(StickerSet) { s ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSend(s) }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(s, fontSize = 34.sp) }
            }
        }
    }
}

/**
 * A queued "Send Later" message: the bubble drawn with a dashed outline in the
 * send colour, plus a caption with its time. Tap for Send Now / Delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduledBubble(message: ScheduledMessage, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    val context = LocalContext.current
    val shape = remember { BubbleShape(outgoing = true, hasTail = true) }
    val stroke = colors.sentBubbleBottom
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 60.dp, end = BubbleEdgeInset, top = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Box(
            modifier = Modifier
                .drawBehind {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(
                        outline,
                        color = stroke,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    )
                }
                .clip(shape)
                .combinedClickable(onClick = onClick)
                .padding(start = 12.dp, end = 12.dp + BubbleTailWidth, top = 7.dp, bottom = 7.dp),
        ) {
            Text(message.body, style = IosType.body, color = colors.primaryText)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp, end = BubbleTailWidth)) {
            Icon(IosIcons.Clock, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(11.dp))
            Text(
                " Send Later · ${TimeFormat.sendLaterLabel(context, message.sendAt)}",
                style = IosType.caption2,
                fontWeight = FontWeight.SemiBold,
                color = colors.secondaryText,
            )
        }
    }
}

/**
 * "Send My Current Location": asks for location permission when needed (either
 * precise or approximate is fine), gets one fix via [CurrentLocation] and hands
 * back a text with a maps link. Location switched off → iOS-style prompt that
 * opens the system Location settings.
 */
@Composable
fun rememberLocationSharer(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    var showOff by remember { mutableStateOf(false) }

    fun fetch() {
        when (CurrentLocation.problem(context)) {
            CurrentLocation.Problem.LOCATION_OFF -> { showOff = true; return }
            CurrentLocation.Problem.NO_PERMISSION -> return
            null -> Unit
        }
        Toast.makeText(context, "Finding your location…", Toast.LENGTH_SHORT).show()
        CurrentLocation.request(context) { loc ->
            if (loc == null) {
                Toast.makeText(context, "Couldn't find your location. Try again near a window or with Wi-Fi on.", Toast.LENGTH_LONG).show()
            } else {
                onResult(CurrentLocation.shareText(loc))
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) fetch()
        else Toast.makeText(context, "Location access is off for Messages. You can allow it in Settings.", Toast.LENGTH_LONG).show()
    }

    if (showOff) {
        AlertDialog(
            onDismissRequest = { showOff = false },
            containerColor = colors.groupedCell,
            title = { Text("Location Services Off", style = IosType.headline) },
            text = { Text("Turn on Location to share where you are.", style = IosType.footnote) },
            confirmButton = {
                TextButton(onClick = {
                    showOff = false
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("Settings", color = colors.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showOff = false }) { Text("Cancel", color = colors.accent) } },
        )
    }

    return {
        if (CurrentLocation.hasPermission(context)) fetch()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

/** Picks a contact with the system picker and returns "Name: number" text. */
@Composable
fun rememberContactSharer(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) onResult("👤 ${c.getString(0)}: ${c.getString(1)}") }
        }
    }
    return {
        runCatching {
            launcher.launch(Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE))
        }
    }
}
