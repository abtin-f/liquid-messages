package com.liquidglass.messages.data.location

import java.util.Locale

/** A place referenced by a message: coordinates plus an optional label. */
data class SharedLocation(
    val latitude: Double,
    val longitude: Double,
    /** "My Location" or whatever label accompanied the link. */
    val label: String?,
    /** The message text minus the map link and label (empty for a pure location). */
    val remainingText: String,
)

/**
 * Recognises map links in plain text so a location can be drawn as a map card
 * instead of a URL — SMS can only carry text, so this is how a shared location
 * travels between phones (and how links from other apps show up too).
 *
 * Understood: Google Maps (`maps.google.com/?q=`, `google.com/maps?q=`,
 * `google.com/maps/@lat,lng`, any country domain), Apple Maps (`maps.apple.com/?ll=` / `q=`),
 * OpenStreetMap (`openstreetmap.org/?mlat=&mlon=`) and `geo:lat,lng` URIs.
 */
object LocationLink {

    private const val NUM = """(-?\d{1,3}(?:\.\d+)?)"""

    private val patterns = listOf(
        Regex("""https?://(?:www\.)?maps\.google\.[a-z.]+/(?:maps)?\??\S*?[?&]q=$NUM,\s*$NUM\S*""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:www\.)?google\.[a-z.]+/maps\S*?[?&]q=$NUM,\s*$NUM\S*""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:www\.)?google\.[a-z.]+/maps/(?:place/[^@\s]*/)?@$NUM,$NUM\S*""", RegexOption.IGNORE_CASE),
        Regex("""https?://maps\.apple\.com/\S*?[?&](?:ll|q|sll)=$NUM,\s*$NUM\S*""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:www\.)?openstreetmap\.org/\S*?[?&]mlat=$NUM&mlon=$NUM\S*""", RegexOption.IGNORE_CASE),
        Regex("""geo:$NUM,$NUM\S*""", RegexOption.IGNORE_CASE),
    )

    /** Leading "📍 My Location" style line that labels the link. */
    private val labelLine = Regex("""^\s*📍\s*(.+?)\s*:?\s*$""")

    fun parse(body: String): SharedLocation? {
        for (p in patterns) {
            val m = p.find(body) ?: continue
            val lat = m.groupValues[1].toDoubleOrNull() ?: continue
            val lng = m.groupValues[2].toDoubleOrNull() ?: continue
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) continue

            var rest = body.removeRange(m.range).trim()
            var label: String? = null
            // Our own format: "📍 My Location\n<link>" (older builds: "📍 My location: <link>").
            val lines = rest.lines().toMutableList()
            val labelIdx = lines.indexOfFirst { labelLine.matches(it) }
            if (labelIdx >= 0) {
                label = labelLine.find(lines[labelIdx])!!.groupValues[1].trim().trimEnd(':').ifBlank { null }
                lines.removeAt(labelIdx)
                rest = lines.joinToString("\n").trim()
            }
            return SharedLocation(lat, lng, label, rest)
        }
        return null
    }

    /** The text we send for a location (readable by any phone / app). */
    fun format(latitude: Double, longitude: Double, label: String = "My Location"): String {
        val lat = String.format(Locale.US, "%.6f", latitude)
        val lng = String.format(Locale.US, "%.6f", longitude)
        return "📍 $label\nhttps://maps.google.com/?q=$lat,$lng"
    }
}
