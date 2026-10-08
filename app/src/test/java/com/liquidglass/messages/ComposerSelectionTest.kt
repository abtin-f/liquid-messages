package com.liquidglass.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import com.liquidglass.messages.ui.chat.MessageInputBar
import com.liquidglass.messages.ui.theme.LiquidMessagesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Double-tapping a word in the composer selects it (Compose only did that on long-press). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ComposerSelectionTest {

    @get:Rule
    val compose = createComposeRule()

    private var text by mutableStateOf("")

    @Test fun doubleTapSelectsTheTappedWord() {
        compose.setContent {
            LiquidMessagesTheme(darkTheme = false) {
                MessageInputBar(text = text, onTextChange = { text = it }, onSend = {}, isSending = false)
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("hello brave world")
        compose.waitForIdle()
        assertEquals("hello brave world", text)

        // "brave" spans characters 6..11; aim at its middle.
        field.performTouchInput { doubleClick(Offset(215f, centerY)) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()

        val selection = field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        println("selection after double tap = $selection")
        assertEquals(TextRange(6, 11), selection)
    }

    @Test fun typingStillWorksAndClearingResets() {
        compose.setContent {
            LiquidMessagesTheme(darkTheme = false) {
                MessageInputBar(text = text, onTextChange = { text = it }, onSend = {}, isSending = false)
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("abc")
        compose.waitForIdle()
        assertEquals("abc", text)
        text = ""   // e.g. cleared after sending
        compose.waitForIdle()
        assertEquals("", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }
}
