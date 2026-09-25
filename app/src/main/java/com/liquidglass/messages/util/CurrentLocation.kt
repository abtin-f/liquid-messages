package com.liquidglass.messages.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-shot "where am I" without Google Play services.
 *
 * Asks every usable provider at once (fused where available, network, and GPS
 * only when precise location was granted — asking GPS with "Approximate" access
 * throws), returns the first fix, and falls back to the freshest last-known
 * location when nothing answers within [TIMEOUT_MS]. Every platform call is
 * guarded: a SecurityException or OEM quirk yields a null result, never a crash.
 */
object CurrentLocation {

    const val TIMEOUT_MS = 12_000L
    private const val FRESH_MS = 2 * 60_000L
    private const val TAG = "CurrentLocation"

    enum class Problem { NO_PERMISSION, LOCATION_OFF }

    fun hasPermission(context: Context): Boolean = coarse(context) || fine(context)

    /** Null when a request can start; otherwise why it can't. */
    fun problem(context: Context): Problem? {
        if (!hasPermission(context)) return Problem.NO_PERMISSION
        val lm = context.getSystemService(LocationManager::class.java) ?: return Problem.LOCATION_OFF
        val enabled = runCatching {
            if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else providers(context, lm).isNotEmpty()
        }.getOrDefault(false)
        return if (enabled && providers(context, lm).isNotEmpty()) null else Problem.LOCATION_OFF
    }

    /** Calls [onResult] exactly once, on the main thread. */
    @SuppressLint("MissingPermission")
    fun request(context: Context, onResult: (Location?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val done = AtomicBoolean(false)
        val lm = context.getSystemService(LocationManager::class.java)
        if (lm == null || !hasPermission(context)) {
            main.post { onResult(null) }
            return
        }
        val candidates = providers(context, lm)
        val cancels = ArrayList<() -> Unit>()

        fun finish(loc: Location?) {
            if (!done.compareAndSet(false, true)) return
            cancels.forEach { runCatching(it) }
            main.post { onResult(loc) }
        }

        // A recent, reasonably accurate cached fix is as good as a new one.
        val cached = bestLastKnown(context, lm)
        if (cached != null && System.currentTimeMillis() - cached.time < FRESH_MS && cached.accuracy < 150f) {
            finish(cached)
            return
        }

        main.postDelayed({ finish(bestLastKnown(context, lm) ?: cached) }, TIMEOUT_MS)

        for (provider in candidates) {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    cancels += { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                        if (loc != null) finish(loc)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) = finish(location)
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit
                        override fun onProviderEnabled(p: String) = Unit
                        override fun onProviderDisabled(p: String) = Unit
                    }
                    cancels += { lm.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                // SecurityException (e.g. GPS with approximate-only access) or a
                // provider that vanished — try the others.
                Log.w(TAG, "Provider $provider unavailable", e)
            }
        }
        if (candidates.isEmpty()) finish(cached)
    }

    /** "📍 My Location" text with a map link (SMS can't carry a map card). */
    fun shareText(loc: Location): String =
        com.liquidglass.messages.data.location.LocationLink.format(loc.latitude, loc.longitude)

    private fun providers(context: Context, lm: LocationManager): List<String> {
        val all = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (fine(context)) add(LocationManager.GPS_PROVIDER)
        }
        return wanted.filter { it in all }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(context: Context, lm: LocationManager): Location? {
        val names = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
            .filter { fine(context) || it != LocationManager.GPS_PROVIDER }
        return names.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun fine(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun coarse(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
