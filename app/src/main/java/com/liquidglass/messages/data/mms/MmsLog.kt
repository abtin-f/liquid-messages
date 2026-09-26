package com.liquidglass.messages.data.mms

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small MMS diagnostic journal. Some phones (Honor, Huawei, some Xiaomi)
 * silence logcat entirely, so every step of an MMS send / download is also
 * written to `Android/data/<app>/files/mms-log.txt` (readable over USB and
 * from Settings). Only technical facts are recorded — sizes, result codes,
 * network state — never message text or phone numbers.
 */
object MmsLog {
    private const val MAX_BYTES = 256 * 1024
    private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var file: File? = null

    fun init(context: Context) {
        if (file != null) return
        file = context.applicationContext.getExternalFilesDir(null)?.let { File(it, "mms-log.txt") }
    }

    fun log(message: String) {
        Log.i("LiquidMms", message)
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_BYTES) {
                // Keep the newest half.
                val keep = f.readText().takeLast(MAX_BYTES / 2)
                f.writeText(keep)
            }
            f.appendText("${time.format(Date())}  $message\n")
        }
    }

    fun read(): String = runCatching { file?.readText().orEmpty() }.getOrDefault("")

    fun clear() {
        runCatching { file?.writeText("") }
    }

    /** One line about the cellular data situation (MMS needs cellular). */
    fun networkState(context: Context): String = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val active = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            active == null -> "none"
            active.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            active.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        }
        val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
        val dataOn = runCatching { tm.isDataEnabled }.getOrNull()
        "active=$transport mobileData=${dataOn ?: "?"} operator=${tm.networkOperator}"
    }.getOrDefault("network=?")

    /** Short name for SmsManager MMS result codes. */
    fun resultName(code: Int): String = when (code) {
        android.app.Activity.RESULT_OK -> "OK"
        1 -> "MMS_ERROR_UNSPECIFIED"
        2 -> "MMS_ERROR_INVALID_APN"
        3 -> "MMS_ERROR_UNABLE_CONNECT_MMS"
        4 -> "MMS_ERROR_HTTP_FAILURE"
        5 -> "MMS_ERROR_IO_ERROR"
        6 -> "MMS_ERROR_RETRY"
        7 -> "MMS_ERROR_CONFIGURATION_ERROR"
        8 -> "MMS_ERROR_NO_DATA_NETWORK"
        9 -> "MMS_ERROR_INVALID_SUBSCRIPTION_ID"
        10 -> "MMS_ERROR_INACTIVE_SUBSCRIPTION"
        11 -> "MMS_ERROR_DATA_DISABLED"
        12 -> "MMS_ERROR_MMS_DISABLED_BY_CARRIER"
        else -> "code $code"
    }
}
