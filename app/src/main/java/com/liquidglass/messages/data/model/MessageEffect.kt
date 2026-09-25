package com.liquidglass.messages.data.model

/**
 * iMessage-style "send with effect" bubble animations. Chosen at send time from
 * the effect picker, played once on send, and replayable by tapping the bubble.
 *
 * SMS carries no effect metadata, so the chosen effect is stored locally per
 * message id (see [com.liquidglass.messages.data.local.MessageMetaStore]).
 */
enum class MessageEffect(val label: String) {
    NONE("None"),
    BIG("Big"),
    SMALL("Small"),
    SHAKE("Shake"),
    RIPPLE("Ripple"),
    EXPLODE("Explode"),
    BLOOM("Bloom"),
    JITTER("Jitter");

    companion object {
        /** Effects offered in the picker (everything except [NONE]). */
        val pickable: List<MessageEffect> = listOf(BIG, SMALL, SHAKE, RIPPLE, EXPLODE, BLOOM, JITTER)

        fun fromName(name: String?): MessageEffect =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}
