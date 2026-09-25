package com.liquidglass.messages.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocationLinkTest {

    @Test
    fun ourOwnFormatRoundTrips() {
        val loc = LocationLink.parse(LocationLink.format(35.6892, 51.389))
        assertNotNull(loc)
        assertEquals(35.6892, loc!!.latitude, 1e-6)
        assertEquals(51.389, loc.longitude, 1e-6)
        assertEquals("My Location", loc.label)
        assertEquals("", loc.remainingText)
    }

    @Test
    fun olderBuildFormatStillParses() {
        val loc = LocationLink.parse("📍 My location: https://maps.google.com/?q=35.700000,51.400000")!!
        assertEquals(35.7, loc.latitude, 1e-6)
        assertEquals("My location", loc.label)
        assertEquals("", loc.remainingText)
    }

    @Test
    fun linksFromOtherAppsParse() {
        assertEquals(48.8584, LocationLink.parse("meet here https://www.google.com/maps/@48.8584,2.2945,17z")!!.latitude, 1e-6)
        assertEquals(37.33, LocationLink.parse("https://maps.apple.com/?ll=37.33,-122.03&q=Apple")!!.latitude, 1e-6)
        assertEquals(-33.8568, LocationLink.parse("geo:-33.8568,151.2153?z=15")!!.latitude, 1e-6)
        assertEquals(51.5, LocationLink.parse("https://www.openstreetmap.org/?mlat=51.5&mlon=-0.12#map=15")!!.latitude, 1e-6)
    }

    @Test
    fun keepsSurroundingText() {
        val loc = LocationLink.parse("I'm here https://maps.google.com/?q=35.1,51.2 come quick")!!
        assertEquals("I'm here  come quick", loc.remainingText)
    }

    @Test
    fun ignoresNonLocations() {
        assertNull(LocationLink.parse("https://example.com/page?q=hello"))
        assertNull(LocationLink.parse("just some text 35.1,51.2"))
        assertNull(LocationLink.parse("geo:200,51"))
    }
}
