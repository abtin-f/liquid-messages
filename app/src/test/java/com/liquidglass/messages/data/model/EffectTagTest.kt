package com.liquidglass.messages.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectTagTest {

    @Test
    fun roundTripsEveryEffect() {
        MessageEffect.pickable.forEach { e ->
            val wire = EffectTag.append("سلام! 🎉", e)
            val parsed = EffectTag.parse(wire)
            assertEquals(e, parsed.effect)
            assertEquals("سلام! 🎉", parsed.text)
        }
    }

    @Test
    fun noEffectLeavesTextAlone() {
        assertEquals("hi", EffectTag.append("hi", MessageEffect.NONE))
        assertEquals(EffectTag.Parsed("hi (sent with love)", MessageEffect.BLOOM).effect, EffectTag.parse("hi (sent with love)").effect)
        assertEquals(MessageEffect.NONE, EffectTag.parse("just text (with brackets)").effect)
        assertEquals(MessageEffect.NONE, EffectTag.parse("(Sent with Banana effect)").effect)
    }

    @Test
    fun understandsIphoneSmsEffects() {
        assertEquals(MessageEffect.SHAKE, EffectTag.parse("Happy birthday!\n(Sent with Slam)").effect)
        assertEquals(MessageEffect.BIG, EffectTag.parse("WOW (Sent with Loud Effect)").effect)
        assertEquals("WOW", EffectTag.parse("WOW (Sent with Loud Effect)").text)
        assertEquals(MessageEffect.EXPLODE, EffectTag.parse("yay (sent with Fireworks)").effect)
    }
}
