package com.liquidglass.messages.ui.components

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.ui.glass.GlassStyle
import com.liquidglass.messages.ui.glass.LocalBackdrop
import com.liquidglass.messages.ui.glass.backdropSource
import com.liquidglass.messages.ui.glass.rememberBackdrop
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * UIKit's presentation layer, rebuilt: UIAlertController (.alert and
 * .actionSheet) plus the small "Copied"-style HUD iOS shows instead of
 * Android toasts. Anything — composable or not — can call [alert],
 * [actionSheet] or [notice]; [IosDialogHost] at the root renders them in
 * iOS 26 Liquid Glass over whatever screen is showing.
 */
object IosDialogs {

    enum class Role { DEFAULT, PREFERRED, CANCEL, DESTRUCTIVE }

    data class Action(val label: String, val role: Role = Role.DEFAULT, val onClick: () -> Unit = {})

    sealed interface Request { val actions: List<Action> }
    data class Alert(val title: String, val message: String?, override val actions: List<Action>) : Request
    data class Sheet(val title: String?, val message: String?, override val actions: List<Action>) : Request

    data class Hud(val text: String, val icon: ImageVector?, val id: Long = System.nanoTime())

    private val queue = mutableStateListOf<Request>()
    internal val current: Request? get() = queue.firstOrNull()

    internal var hud by mutableStateOf<Hud?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /** A centred alert. With no actions an "OK" button is added. */
    fun alert(title: String, message: String? = null, vararg actions: Action) = onMain {
        queue += Alert(title, message, actions.toList().ifEmpty { listOf(Action("OK", Role.PREFERRED)) })
    }

    /** A bottom action sheet; a Cancel button is appended if none is given. */
    fun actionSheet(title: String? = null, message: String? = null, vararg actions: Action) = onMain {
        val list = actions.toList()
        queue += Sheet(title, message, if (list.any { it.role == Role.CANCEL }) list else list + Action("Cancel", Role.CANCEL))
    }

    /** The glass capsule iOS drops from the top for quick confirmations. */
    fun notice(text: String, icon: ImageVector? = IosIcons.Check) = onMain { hud = Hud(text, icon) }

    /** Dismisses the visible dialog, running [action] afterwards. */
    internal fun resolve(request: Request, action: Action?) {
        queue.remove(request)
        action?.onClick?.invoke()
    }

    internal fun clearHud(h: Hud) {
        if (hud == h) hud = null
    }

    /** Test/preview helper. */
    fun dismissAll() {
        queue.clear()
        hud = null
    }
}

/**
 * Hosts the app and draws [IosDialogs] above it. The app's pixels become the
 * backdrop the alert glass frosts, exactly like UIKit's presentation.
 */
@Composable
fun IosDialogHost(content: @Composable () -> Unit) {
    val backdrop = rememberBackdrop()
    val request = IosDialogs.current
    val colors = LiquidTheme.colors

    // Record the app into the backdrop only while something is on screen that
    // needs it; recording every frame otherwise doubled the draw work.
    val needsBackdrop = request != null || IosDialogs.hud != null
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (needsBackdrop) Modifier.backdropSource(backdrop) else Modifier)) { content() }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            // Dimming: iOS alerts dim ~20%, action sheets a little more.
            AnimatedVisibility(visible = request != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (colors.isDark) 0.48f else 0.22f))
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            // Tapping outside cancels a sheet; alerts ignore it (UIKit).
                            val r = request
                            if (r is IosDialogs.Sheet) IosDialogs.resolve(r, r.actions.firstOrNull { it.role == IosDialogs.Role.CANCEL })
                        },
                )
            }

            if (request != null) {
                BackHandler {
                    IosDialogs.resolve(request, request.actions.firstOrNull { it.role == IosDialogs.Role.CANCEL })
                }
            }

            val alert = request as? IosDialogs.Alert
            var lastAlert by remember { mutableStateOf<IosDialogs.Alert?>(null) }
            if (alert != null) lastAlert = alert
            AnimatedVisibility(
                visible = alert != null,
                enter = scaleIn(spring(dampingRatio = 0.72f, stiffness = 480f), initialScale = 1.18f) + fadeIn(tween(160)),
                exit = scaleOut(tween(180), targetScale = 0.94f) + fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                lastAlert?.let { IosAlertCard(it) }
            }

            val sheet = request as? IosDialogs.Sheet
            var lastSheet by remember { mutableStateOf<IosDialogs.Sheet?>(null) }
            if (sheet != null) lastSheet = sheet
            AnimatedVisibility(
                visible = sheet != null,
                enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it },
                exit = slideOutVertically(tween(220)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                lastSheet?.let { IosActionSheetCard(it) }
            }

            HudLayer(Modifier.align(Alignment.TopCenter))
        }
    }
}

private val AlertShape = RoundedCornerShape(34.dp)

/**
 * iOS 26 alert: a frosted glass card with leading-aligned bold title and
 * message, then capsule buttons — side by side for two actions, stacked for
 * more. The preferred action is filled with the accent colour.
 */
@Composable
fun IosAlertCard(alert: IosDialogs.Alert, modifier: Modifier = Modifier) {
    val colors = LiquidTheme.colors
    Column(
        modifier = modifier
            .padding(horizontal = 40.dp)
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .glassControl(AlertShape, colors.glassShadow, colors, GlassStyle.Menu)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp),
    ) {
        Text(alert.title, style = IosType.headline, color = colors.primaryText)
        if (!alert.message.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(alert.message, style = IosType.subheadline, color = colors.primaryText.copy(alpha = 0.78f))
        }
        Spacer(Modifier.height(20.dp))
        // UIKit puts Cancel on the leading side of a two-button alert.
        val ordered = alert.actions.sortedBy { if (it.role == IosDialogs.Role.CANCEL) 0 else 1 }
        if (ordered.size == 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ordered.forEach { AlertButton(alert, it, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alert.actions.sortedBy { if (it.role == IosDialogs.Role.CANCEL) 1 else 0 }
                    .forEach { AlertButton(alert, it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun AlertButton(request: IosDialogs.Request, action: IosDialogs.Action, modifier: Modifier) {
    val colors = LiquidTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val preferred = action.role == IosDialogs.Role.PREFERRED
    val fill = when {
        preferred -> colors.accent
        colors.isDark -> Color(0x33FFFFFF)
        else -> Color(0x14000000)
    }
    val textColor = when (action.role) {
        IosDialogs.Role.PREFERRED -> Color.White
        IosDialogs.Role.DESTRUCTIVE -> colors.destructive
        else -> colors.primaryText
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer {
                val s = if (pressed) 0.96f else 1f
                scaleX = s; scaleY = s
            }
            .clip(RoundedCornerShape(50))
            .background(if (pressed) fill.copy(alpha = (fill.alpha * 1.6f).coerceAtMost(1f)) else fill)
            .clickable(interaction, indication = null) { IosDialogs.resolve(request, action) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            action.label,
            style = IosType.body,
            fontWeight = if (preferred || action.role == IosDialogs.Role.DESTRUCTIVE) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}

/**
 * iOS action sheet: a glass group of centred choices with an optional small
 * grey title, and Cancel in its own card underneath.
 */
@Composable
fun IosActionSheetCard(sheet: IosDialogs.Sheet, modifier: Modifier = Modifier) {
    val colors = LiquidTheme.colors
    val shape = RoundedCornerShape(28.dp)
    val cancel = sheet.actions.firstOrNull { it.role == IosDialogs.Role.CANCEL }
    val others = sheet.actions.filter { it !== cancel }
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.fillMaxWidth().glassControl(shape, colors.glassShadow, colors, GlassStyle.Menu)) {
            if (sheet.title != null || sheet.message != null) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    sheet.title?.let {
                        Text(it, style = IosType.footnote, fontWeight = FontWeight.SemiBold, color = colors.secondaryText, textAlign = TextAlign.Center)
                    }
                    sheet.message?.let {
                        Text(it, style = IosType.footnote, color = colors.secondaryText, textAlign = TextAlign.Center)
                    }
                }
                SheetDivider()
            }
            others.forEachIndexed { i, action ->
                SheetButton(sheet, action)
                if (i < others.lastIndex) SheetDivider()
            }
        }
        if (cancel != null) {
            Box(Modifier.fillMaxWidth().glassControl(shape, colors.glassShadow, colors, GlassStyle.Menu)) {
                SheetButton(sheet, cancel)
            }
        }
    }
}

@Composable
private fun SheetDivider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(LiquidTheme.colors.divider))
}

@Composable
private fun SheetButton(sheet: IosDialogs.Sheet, action: IosDialogs.Action) {
    val colors = LiquidTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(57.dp)
            .background(if (pressed) colors.divider.copy(alpha = 0.35f) else Color.Transparent)
            .clickable(interaction, indication = null) { IosDialogs.resolve(sheet, action) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            action.label,
            style = IosType.body.copy(fontSize = IosType.body.fontSize * 1.05f),
            fontWeight = if (action.role == IosDialogs.Role.CANCEL) FontWeight.SemiBold else FontWeight.Normal,
            color = if (action.role == IosDialogs.Role.DESTRUCTIVE) colors.destructive else colors.accent,
        )
    }
}

/** The glass HUD capsule ("Copied", "Pinned"…) that drops in from the top. */
@Composable
private fun HudLayer(modifier: Modifier) {
    val colors = LiquidTheme.colors
    val haptics = LocalHapticFeedback.current
    val hud = IosDialogs.hud
    var shown by remember { mutableStateOf<IosDialogs.Hud?>(null) }
    if (hud != null) shown = hud
    LaunchedEffect(hud) {
        if (hud != null) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(1900)
            IosDialogs.clearHud(hud)
        }
    }
    AnimatedVisibility(
        visible = hud != null,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically(tween(240)) { -it * 2 } + fadeOut(tween(240)),
        modifier = modifier.statusBarsPadding().padding(top = 8.dp),
    ) {
        shown?.let { h ->
            Row(
                modifier = Modifier
                    .glassControl(RoundedCornerShape(50), colors.glassShadow, colors, GlassStyle.Menu)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                h.icon?.let {
                    Icon(it, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(h.text, style = IosType.subheadline, fontWeight = FontWeight.SemiBold, color = colors.primaryText)
            }
        }
    }
}

/**
 * An iOS page sheet (UISheetPresentationController): grouped background,
 * big rounded top corners and the small grey grabber instead of Material's
 * drag handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosSheet(
    onDismiss: () -> Unit,
    containerColor: Color = LiquidTheme.colors.groupedBackground,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LiquidTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = containerColor,
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        scrimColor = Color.Black.copy(alpha = if (colors.isDark) 0.45f else 0.2f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 6.dp, bottom = 10.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.tertiaryText),
            )
        },
        content = content,
    )
}

/** Title bar of an iOS sheet: Cancel · Title · Done. */
@Composable
fun IosSheetBar(
    title: String,
    onCancel: (() -> Unit)? = null,
    doneLabel: String? = null,
    onDone: (() -> Unit)? = null,
) {
    val colors = LiquidTheme.colors
    Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp)) {
        if (onCancel != null) {
            Text(
                "Cancel", style = IosType.body, color = colors.accent,
                modifier = Modifier.align(Alignment.CenterStart).clickable(remember { MutableInteractionSource() }, null, onClick = onCancel),
            )
        }
        Text(title, style = IosType.headline, color = colors.primaryText, modifier = Modifier.align(Alignment.Center))
        if (onDone != null && doneLabel != null) {
            Text(
                doneLabel, style = IosType.body, fontWeight = FontWeight.SemiBold, color = colors.accent,
                modifier = Modifier.align(Alignment.CenterEnd).clickable(remember { MutableInteractionSource() }, null, onClick = onDone),
            )
        }
    }
}

private val WheelRow = 36.dp
private const val WheelVisible = 5

/**
 * One column of a UIPickerView: rows fade and shrink away from the centre
 * band, flings snap to a row, and each settle ticks the haptics.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosWheel(
    labels: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Center,
) {
    val colors = LiquidTheme.colors
    val haptics = LocalHapticFeedback.current
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, (labels.size - 1).coerceAtLeast(0)))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val pad = WheelVisible / 2
    val current by androidx.compose.runtime.rememberUpdatedState(selected)
    val report by androidx.compose.runtime.rememberUpdatedState(onSelected)

    LaunchedEffect(state, labels.size) {
        snapshotFlow { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > 0 && state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.let { state.firstVisibleItemScrollOffset > it / 2 } == true) 1 else 0 }
            .distinctUntilChanged()
            .collect { idx ->
                val i = idx.coerceIn(0, labels.lastIndex)
                if (i != current) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    report(i)
                }
            }
    }

    Box(modifier.height(WheelRow * WheelVisible)) {
        LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxSize()) {
            items(pad) { Spacer(Modifier.height(WheelRow)) }
            items(labels.size) { i ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WheelRow)
                        .graphicsLayer {
                            val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i + pad }
                            if (info != null) {
                                val center = state.layoutInfo.viewportEndOffset / 2f
                                val d = abs(info.offset + info.size / 2f - center) / (info.size * pad.toFloat())
                                val k = d.coerceIn(0f, 1f)
                                alpha = 1f - 0.7f * k
                                scaleX = 1f - 0.12f * k
                                scaleY = 1f - 0.2f * k
                                transformOrigin = TransformOrigin.Center
                            }
                        },
                    contentAlignment = when (align) {
                        TextAlign.Start -> Alignment.CenterStart
                        TextAlign.End -> Alignment.CenterEnd
                        else -> Alignment.Center
                    },
                ) {
                    Text(labels[i], style = IosType.title2.copy(fontWeight = FontWeight.Normal, fontSize = IosType.body.fontSize * 1.2f), color = colors.primaryText, maxLines = 1)
                }
            }
            items(pad) { Spacer(Modifier.height(WheelRow)) }
        }
    }
}

/** The grey rounded band behind the selected row of a set of [IosWheel]s. */
@Composable
fun BoxScope.IosWheelBand(horizontalInset: Dp = 8.dp) {
    Box(
        Modifier
            .align(Alignment.Center)
            .padding(horizontal = horizontalInset)
            .fillMaxWidth()
            .height(WheelRow)
            .clip(RoundedCornerShape(9.dp))
            .background(LiquidTheme.colors.fieldBackground),
    )
}
