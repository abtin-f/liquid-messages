package com.liquidglass.messages.ui.chat.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.data.model.MessageEffect
import com.liquidglass.messages.ui.theme.LiquidTheme
import kotlinx.coroutines.delay

/**
 * iMessage "Send with effect" sheet. Presents every [MessageEffect.pickable]
 * effect as a 2-column grid of cards, each running a *live* looping preview of
 * its own animation (via [EffectedBubble]). The [current] selection gets an
 * accent ring; a dedicated "None" card clears any effect.
 *
 * Tapping a card invokes [onSelect] then [onDismiss] so the caller can apply the
 * effect and close in one gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectPicker(
    current: MessageEffect,
    onSelect: (MessageEffect) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.chatBackground,
        scrimColor = Color.Black.copy(alpha = 0.32f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "Send with effect",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 14.dp),
                textAlign = TextAlign.Center,
            )

            // "None" — clears any active effect. Highlighted when current == NONE.
            NoneCard(
                selected = current == MessageEffect.NONE,
                onClick = {
                    onSelect(MessageEffect.NONE)
                    onDismiss()
                },
            )

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(MessageEffect.pickable, key = { it.name }) { effect ->
                    EffectCard(
                        effect = effect,
                        selected = effect == current,
                        onClick = {
                            onSelect(effect)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/** Full-width "None" row that visually reads as a clear/reset option. */
@Composable
private fun NoneCard(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val ring = if (selected) colors.accent else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.receivedBubble)
            .border(2.dp, ring, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .border(1.5.dp, colors.secondaryText, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // A slash conveys "no effect" without depending on extended icon packs.
            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 1.5.dp)
                    .background(colors.secondaryText),
            )
        }
        Spacer(Modifier.size(14.dp))
        Text(
            text = "None",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = colors.primaryText,
        )
    }
}

/**
 * A single effect card with a live, looping preview. The preview is a mini sent
 * bubble wrapped in [EffectedBubble]; we bump an internal trigger on a timer so
 * the animation replays continuously while the sheet is open.
 */
@Composable
private fun EffectCard(
    effect: MessageEffect,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val ring = if (selected) colors.accent else colors.divider

    // Looping trigger so each card animates on its own without user input.
    var trigger by remember { mutableIntStateOf(1) }
    LaunchedEffect(effect) {
        while (true) {
            delay(2000L)
            trigger++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.receivedBubble)
            .border(2.dp, ring, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            EffectedBubble(effect = effect, trigger = trigger) {
                PreviewBubble()
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = effect.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A tiny stand-in sent bubble used as the animated subject in each preview. */
@Composable
private fun PreviewBubble() {
    val colors = LiquidTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors.sentBubbleGradient,
                ),
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Three dots evoke message text without locale/font concerns.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.sentText.copy(alpha = 0.9f)),
                )
            }
        }
    }
}
