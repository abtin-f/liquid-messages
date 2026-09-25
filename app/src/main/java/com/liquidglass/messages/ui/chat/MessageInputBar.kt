package com.liquidglass.messages.ui.chat

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.data.model.MessageEffect
import com.liquidglass.messages.ui.components.GlassCapsule
import com.liquidglass.messages.ui.components.GlassCircleButton
import com.liquidglass.messages.ui.components.IosIcons
import com.liquidglass.messages.ui.theme.IosType
import com.liquidglass.messages.ui.theme.LiquidTheme
import com.liquidglass.messages.ui.theme.fontFamilyFor

/**
 * iOS 26 composer: a floating glass "+" button beside a glass capsule text
 * field. Inside the field's trailing edge sits the dictation mic while it's
 * empty, swapping to the round coloured ↑ send button once there's text.
 * Long-pressing send opens "Send with Effect", like iOS.
 */
@Composable
fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    modifier: Modifier = Modifier,
    selectedEffect: MessageEffect = MessageEffect.NONE,
    onRequestEffects: () -> Unit = {},
    /** Opens the "+" apps menu; null falls back to the effect picker. */
    onPlus: (() -> Unit)? = null,
    /** Pending "Send Later" time, shown as a pill above the field. */
    sendLaterAt: Long? = null,
    onSendLaterClick: () -> Unit = {},
    onClearSendLater: () -> Unit = {},
    showCharacterCount: Boolean = false,
    /** Photos / files waiting to go out with this message (shown inside the field). */
    attachments: List<android.net.Uri> = emptyList(),
    onRemoveAttachment: (android.net.Uri) -> Unit = {},
    /** "Replying to …" preview shown at the top of the field. */
    replyPreview: ReplyPreview? = null,
    onCancelReply: () -> Unit = {},
) {
    val colors = LiquidTheme.colors
    val canSend = text.isNotBlank() || attachments.isNotEmpty()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val currentText by rememberUpdatedState(text)

    // Voice-to-text: the on-device recognizer appends to whatever is typed.
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onTextChange(if (currentText.isBlank()) spoken else "$currentText $spoken")
            }
        }
    }
    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        runCatching { voiceLauncher.launch(intent) }.onFailure {
            Toast.makeText(context, "Dictation isn't available on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (sendLaterAt != null) {
            GlassCapsule(
                onClick = onSendLaterClick,
                modifier = Modifier.padding(start = 62.dp, bottom = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                ) {
                    Icon(IosIcons.Clock, contentDescription = null, tint = colors.accent, modifier = Modifier.size(15.dp))
                    Text(
                        text = "  " + com.liquidglass.messages.util.TimeFormat.sendLaterLabel(context, sendLaterAt),
                        style = IosType.footnote,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent,
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(colors.tertiaryText)
                            .clickable(onClick = onClearSendLater),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(IosIcons.Close, contentDescription = "Cancel Send Later", tint = Color.White, modifier = Modifier.size(9.dp))
                    }
                }
            }
        }
        // Pending send effect: a small pill above the field, tap to change.
        if (selectedEffect != MessageEffect.NONE) {
            GlassCapsule(
                onClick = onRequestEffects,
                modifier = Modifier.padding(start = 68.dp, bottom = 6.dp),
            ) {
                Text(
                    text = "Sending with ${selectedEffect.label}",
                    style = IosType.footnote,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassCircleButton(
                icon = IosIcons.Plus,
                contentDescription = "Apps",
                onClick = { (onPlus ?: onRequestEffects)() },
                size = 42.dp,
                iconSize = 20.dp,
                tint = colors.secondaryText,
            )

            GlassCapsule(
                cornerRadius = 21.dp,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp),
            ) {
              Column {
                if (replyPreview != null) {
                    ReplyComposerBar(replyPreview, onCancel = onCancelReply)
                }
                if (attachments.isNotEmpty()) {
                    // iOS shows pending media inside the field, above the text.
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(attachments.size) { i ->
                            val uri = attachments[i]
                            Box {
                                coil.compose.AsyncImage(
                                    model = uri,
                                    contentDescription = "Attachment",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                        .background(colors.fieldBackground),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .clickable { onRemoveAttachment(uri) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(IosIcons.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            modifier = Modifier.fillMaxWidth(),
                            // Paragraph direction follows the first strong letter:
                            // Persian flows right-to-left and hugs the right edge,
                            // English stays left-to-right — like iOS / Telegram.
                            textStyle = IosType.body.copy(
                                color = colors.primaryText,
                                fontFamily = fontFamilyFor(text),
                                textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            decorationBox = { inner ->
                                if (text.isEmpty()) {
                                    Text(
                                        text = "Text Message",
                                        style = IosType.body,
                                        color = colors.tertiaryText,
                                    )
                                }
                                inner()
                            },
                        )
                    }

                    AnimatedContent(
                        targetState = canSend,
                        transitionSpec = {
                            (scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), initialScale = 0.4f) +
                                fadeIn(tween(120))) togetherWith
                                (scaleOut(tween(100), targetScale = 0.4f) + fadeOut(tween(100)))
                        },
                        label = "sendMicSwap",
                        modifier = Modifier.padding(end = 5.dp, bottom = 5.dp),
                    ) { showSend ->
                        if (showSend) {
                          Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (showCharacterCount) {
                                Text(
                                    text = smsCharacterCount(text),
                                    style = IosType.caption2,
                                    color = colors.secondaryText,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            // ONE gesture detector for both tap (send) and long-press
                            // (effects). The old version nested a clickable inside a
                            // tap detector, which swallowed the tap — send never fired.
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(colors.sentBubbleGradient))
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { onSend() },
                                            onLongPress = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onRequestEffects()
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (sendLaterAt != null) IosIcons.Clock else IosIcons.ArrowUp,
                                    contentDescription = if (sendLaterAt != null) "Schedule" else "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                          }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { startVoice() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = IosIcons.Mic,
                                    contentDescription = "Dictation",
                                    tint = colors.tertiaryText,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                    }
                }
              }
            }
        }
    }
}

/**
 * iOS "Character Count": characters used of the current SMS segment, with the
 * number of messages it will be split into when more than one ("2 · 45/153").
 */
private fun smsCharacterCount(text: String): String {
    val r = runCatching { android.telephony.SmsMessage.calculateLength(text, false) }.getOrNull()
        ?: return "${text.length}/160"
    val parts = r[0]
    val used = r[1]
    val remaining = r[2]
    val perPart = if (parts <= 1) used + remaining else (used + remaining) / parts
    val inPart = if (parts <= 1) used else used - perPart * (parts - 1)
    return if (parts > 1) "$parts · $inPart/$perPart" else "$used/${used + remaining}"
}
