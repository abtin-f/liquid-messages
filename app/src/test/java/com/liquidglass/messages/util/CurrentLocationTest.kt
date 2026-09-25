package com.liquidglass.messages.util

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurrentLocationTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private val lm = app.getSystemService(LocationManager::class.java)

    private fun fix(provider: String, lat: Double, ageMs: Long = 0) = Location(provider).apply {
        latitude = lat
        longitude = 51.389
        accuracy = 30f
        time = System.currentTimeMillis() - ageMs
        elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
    }

    private fun run(): Array<Location?> {
        val out = arrayOfNulls<Location>(1)
        var called = 0
        CurrentLocation.request(app) { out[0] = it; called++ }
        shadowOf(Looper.getMainLooper()).idleFor(CurrentLocation.TIMEOUT_MS + 1_000, TimeUnit.MILLISECONDS)
        assertEquals("callback must fire exactly once", 1, called)
        return out
    }

    @Test
    fun approximateOnlyDoesNotCrashAndUsesNetwork() {
        // "Approximate" in the Android 12+ dialog = coarse only; GPS is still switched on.
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        shadowOf(lm).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, fix(LocationManager.NETWORK_PROVIDER, 35.7))

        val result = run()[0]
        assertNotNull(result)
        assertEquals(35.7, result!!.latitude, 1e-6)
    }

    @Test
    fun preciseUsesFreshCachedFixImmediately() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(lm).setLastKnownLocation(LocationManager.GPS_PROVIDER, fix(LocationManager.GPS_PROVIDER, 32.6, ageMs = 30_000))
        assertEquals(32.6, run()[0]!!.latitude, 1e-6)
    }

    @Test
    fun noFixTimesOutWithNullInsteadOfHanging() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        assertNull(run()[0])
    }

    @Test
    fun withoutPermissionReportsIt() {
        assertEquals(CurrentLocation.Problem.NO_PERMISSION, CurrentLocation.problem(app))
    }

    @Test
    fun shareTextHasMapLink() {
        val t = CurrentLocation.shareText(fix("x", 35.6892))
        assertTrue(t.contains("https://maps.google.com/?q=35.689200,51.389000"))
    }
}
