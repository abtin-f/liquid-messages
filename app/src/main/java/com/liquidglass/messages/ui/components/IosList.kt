package com.liquidglass.messages.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.ui.glass.LocalBackdrop
import com.liquidglass.messages.ui.glass.backdropSource
import com.liquidglass.messages.ui.glass.rememberBackdrop
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme

/** iOS 26 inset-grouped corner radius (noticeably rounder than iOS 17's 10pt). */
private val GroupRadius = 22.dp

/**
 * iOS page: grouped background, floating glass back button, a large title that
 * scrolls away and reappears small in the bar — the layout of every Settings
 * screen. The content scrolls under Liquid Glass chrome.
 */
@Composable
fun IosGroupedPage(
    title: String,
    onBack: () -> Unit,
    backLabel: String? = null,
    /** Small title shown in the bar once scrolled (defaults to [title]). */
    barTitle: String = title,
    content: LazyListScope.() -> Unit,
) {
    val colors = LiquidTheme.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60 }
    }
    val backdrop = rememberBackdrop()

    Box(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .backdropSource(backdrop)
                .background(colors.groupedBackground),
            contentPadding = PaddingValues(top = statusTop + 60.dp, bottom = navBottom + 32.dp),
        ) {
            if (title.isNotBlank()) item(key = "__title") {
                Text(
                    text = title,
                    style = IosType.largeTitle,
                    color = colors.primaryText,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
            }
            content()
        }

        EdgeFadeColored(colors.groupedBackground, height = statusTop + 64.dp, modifier = Modifier.align(Alignment.TopCenter), visible = collapsed)

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
            ) {
                if (backLabel == null) {
                    GlassCircleButton(
                        icon = IosIcons.ChevronLeft,
                        contentDescription = "Back",
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                } else {
                    GlassCapsule(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).height(44.dp)) {
                        Text(
                            text = backLabel,
                            style = IosType.body,
                            color = colors.primaryText,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                if (collapsed && barTitle.isNotBlank()) {
                    Text(
                        text = barTitle,
                        style = IosType.headline,
                        color = colors.primaryText,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun EdgeFadeColored(color: Color, height: androidx.compose.ui.unit.Dp, modifier: Modifier, visible: Boolean) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(color, color.copy(alpha = 0.85f), color.copy(alpha = 0f)),
                ),
            ),
    )
}

/** An inset-grouped section with optional small-caps header and footer. */
fun LazyListScope.iosSection(
    key: String,
    header: String? = null,
    footer: String? = null,
    rows: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) {
        val colors = LiquidTheme.colors
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (header != null) {
                Text(
                    text = header.uppercase(),
                    style = IosType.footnote,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(start = 20.dp, bottom = 7.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(GroupRadius))
                    .background(colors.groupedCell),
                content = rows,
            )
            if (footer != null) {
                Text(
                    text = footer,
                    style = IosType.footnote,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 7.dp),
                )
            }
        }
    }
}

/**
 * One iOS table cell: optional Settings-style icon tile, title, trailing value /
 * accessory (chevron, checkmark or custom), and a separator inset to the text.
 */
@Composable
fun IosRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.White,
    iconBackground: Color? = null,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = LiquidTheme.colors.primaryText,
    chevron: Boolean = false,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = LiquidTheme.colors
    val textStart = if (icon != null) 16.dp + 30.dp + 14.dp else 20.dp
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 52.dp)
                .padding(start = if (icon != null) 16.dp else 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                if (iconBackground != null) {
                    IconTile(icon, iconBackground, iconTint)
                } else {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = IosType.body, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = IosType.footnote, color = colors.secondaryText)
                }
            }
            if (value != null) {
                Text(
                    value,
                    style = IosType.body,
                    color = colors.secondaryText,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            trailing?.invoke(this)
            if (chevron) {
                Icon(
                    IosIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.tertiaryText,
                    modifier = Modifier.padding(start = 8.dp).size(13.dp),
                )
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = textStart)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.divider),
            )
        }
    }
}

/** Settings-style rounded-square coloured icon with a white glyph. */
@Composable
fun IconTile(icon: ImageVector, background: Color, tint: Color = Color.White, size: androidx.compose.ui.unit.Dp = 30.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.62f))
    }
}

/** The iOS switch: 51×31, green track, white knob with a soft shadow, spring motion. */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = LiquidTheme.colors
    val track by animateColorAsState(
        targetValue = if (checked) Color(0xFF34C759) else if (colors.isDark) Color(0xFF39393D) else Color(0xFFE9E9EA),
        label = "switchTrack",
    )
    val knobX by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "switchKnob",
    )
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 2.dp)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}
