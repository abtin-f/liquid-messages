package com.liquidglass.messages.data.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.location.Geocoder
import android.util.Log
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Renders a small map snapshot around a point from OpenStreetMap tiles — no
 * Google Play services or API key — for iMessage-style location cards.
 *
 * Tiles are cached on disk (cache/tiles) and finished snapshots in memory, so a
 * conversation full of locations costs a handful of tile requests once. Follows
 * the OSM tile usage policy: identifying User-Agent, caching, and on-screen
 * attribution (drawn by the card).
 */
object StaticMap {

    const val ZOOM = 16
    private const val TILE = 256
    private const val TAG = "StaticMap"
    private const val USER_AGENT = "LiquidMessages/1.6 (Android SMS app; location previews)"

    private val snapshots = LruCache<String, Bitmap>(12)
    private val addresses = LruCache<String, String>(64)

    /**
     * A [widthPx]×[heightPx] map centred on the point, or null when no tile could
     * be loaded (offline / blocked) — the card then draws its own placeholder.
     * Blocking: call from a background dispatcher.
     */
    fun render(context: Context, lat: Double, lng: Double, widthPx: Int, heightPx: Int, zoom: Int = ZOOM): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val key = "%.5f,%.5f,%d,%d,%d".format(Locale.US, lat, lng, widthPx, heightPx, zoom)
        snapshots.get(key)?.let { return it }

        val (cx, cy) = project(lat, lng, zoom)
        val left = cx - widthPx / 2.0
        val top = cy - heightPx / 2.0
        val n = 1 shl zoom
        val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        var drew = 0
        for (ty in floor(top / TILE).toInt()..floor((top + heightPx) / TILE).toInt()) {
            if (ty !in 0 until n) continue
            for (txRaw in floor(left / TILE).toInt()..floor((left + widthPx) / TILE).toInt()) {
                val tx = ((txRaw % n) + n) % n // wrap across the antimeridian
                val tile = tile(context, zoom, tx, ty) ?: continue
                canvas.drawBitmap(tile, (txRaw * TILE - left).toFloat(), (ty * TILE - top).toFloat(), null)
                drew++
            }
        }
        if (drew == 0) return null
        snapshots.put(key, out)
        return out
    }

    /** Web-Mercator pixel coordinates of a point at [zoom] (256-px tiles). */
    internal fun project(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
        val n = (1 shl zoom).toDouble()
        val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val x = (lng + 180.0) / 360.0 * n * TILE
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n * TILE
        return x to y
    }

    private fun tile(context: Context, z: Int, x: Int, y: Int): Bitmap? {
        val file = File(context.cacheDir, "tiles/$z/$x/$y.png")
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < 30L * 24 * 3600 * 1000) {
            BitmapFactory.decodeFile(file.path)?.let { return it }
        }
        return try {
            val conn = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
            conn.connectTimeout = 6_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (conn.responseCode != 200) return null
                val bytes = conn.inputStream.use { it.readBytes() }
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tile $z/$x/$y unavailable: ${e.message}")
            null
        }
    }

    /**
     * Short human address ("Enghelab St, Tehran") via the platform geocoder, or
     * null when there's no geocoder / network. Blocking.
     */
    @Suppress("DEPRECATION")
    fun address(context: Context, lat: Double, lng: Double): String? {
        val key = "%.4f,%.4f".format(Locale.US, lat, lng)
        addresses.get(key)?.let { return it }
        if (!Geocoder.isPresent()) return null
        return runCatching {
            val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull() ?: return null
            listOfNotNull(a.thoroughfare ?: a.subLocality, a.locality ?: a.adminArea)
                .distinct()
                .joinToString(", ")
                .ifBlank { a.countryName }
        }.getOrNull()?.also { addresses.put(key, it) }
    }
}
