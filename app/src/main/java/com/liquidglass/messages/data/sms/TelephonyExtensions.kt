package com.liquidglass.messages.data.sms

import android.database.Cursor
import android.provider.Telephony

/**
 * Null-safe / defensive helpers for reading values out of a Telephony [Cursor],
 * plus the canonical set of column names used across the SMS data layer.
 *
 * The system provider is permissive about which columns it returns and may hand
 * back `null` or absent columns on some OEM ROMs, so every accessor here tolerates
 * a missing column index (`getColumnIndex` returning -1) and a null cell value.
 */

/**
 * Returns the string in [column], or `null` when the column is absent or the
 * cell value is null.
 */
fun Cursor.getStringOrNull(column: String): String? {
    val index = getColumnIndex(column)
    if (index < 0 || isNull(index)) return null
    return getString(index)
}

/**
 * Returns the long in [column], or `0L` when the column is absent or null.
 */
fun Cursor.getLongOrZero(column: String): Long {
    val index = getColumnIndex(column)
    if (index < 0 || isNull(index)) return 0L
    return getLong(index)
}

/**
 * Returns the int in [column], or [def] when the column is absent or null.
 */
fun Cursor.getIntOrDefault(column: String, def: Int): Int {
    val index = getColumnIndex(column)
    if (index < 0 || isNull(index)) return def
    return getInt(index)
}

/**
 * Centralised column-name constants for the SMS / threads providers.
 *
 * Most map straight onto [android.provider.Telephony] constants; a couple
 * (SIMPLE_THREADS_*) live on the un-exported `Telephony.ThreadsColumns` so we
 * mirror their literal values here to stay compile-safe across SDKs.
 */
object SmsColumns {

    // --- content://sms columns ---
    const val ID: String = Telephony.Sms._ID
    const val THREAD_ID: String = Telephony.Sms.THREAD_ID
    const val ADDRESS: String = Telephony.Sms.ADDRESS
    const val BODY: String = Telephony.Sms.BODY
    const val DATE: String = Telephony.Sms.DATE
    const val DATE_SENT: String = Telephony.Sms.DATE_SENT
    const val TYPE: String = Telephony.Sms.TYPE
    const val READ: String = Telephony.Sms.READ
    const val SEEN: String = Telephony.Sms.SEEN
    const val STATUS: String = Telephony.Sms.STATUS
    const val SUBSCRIPTION_ID: String = Telephony.Sms.SUBSCRIPTION_ID

    // --- content://mms-sms/conversations?simple=true (thread summary) columns ---
    // These mirror Telephony.ThreadsColumns, which is not part of the public API
    // surface, so we hard-code the well-known literal column names.
    const val THREADS_ID: String = Telephony.Threads._ID
    const val THREADS_RECIPIENT_IDS: String = "recipient_ids"
    const val THREADS_SNIPPET: String = "snippet"
    const val THREADS_DATE: String = "date"
    const val THREADS_MESSAGE_COUNT: String = "message_count"
    const val THREADS_READ: String = "read"

    // --- Telephony.Sms.TYPE values ---
    const val MESSAGE_TYPE_INBOX: Int = Telephony.Sms.MESSAGE_TYPE_INBOX     // 1
    const val MESSAGE_TYPE_SENT: Int = Telephony.Sms.MESSAGE_TYPE_SENT       // 2
    const val MESSAGE_TYPE_DRAFT: Int = Telephony.Sms.MESSAGE_TYPE_DRAFT     // 3
    const val MESSAGE_TYPE_OUTBOX: Int = Telephony.Sms.MESSAGE_TYPE_OUTBOX   // 4
    const val MESSAGE_TYPE_FAILED: Int = Telephony.Sms.MESSAGE_TYPE_FAILED   // 5
    const val MESSAGE_TYPE_QUEUED: Int = Telephony.Sms.MESSAGE_TYPE_QUEUED   // 6

    // --- Telephony.Sms.STATUS values ---
    const val STATUS_NONE: Int = Telephony.Sms.STATUS_NONE           // -1
    const val STATUS_COMPLETE: Int = Telephony.Sms.STATUS_COMPLETE   // 0
    const val STATUS_PENDING: Int = Telephony.Sms.STATUS_PENDING     // 32
    const val STATUS_FAILED: Int = Telephony.Sms.STATUS_FAILED       // 64
}

/**
 * Maps a raw [subscriptionId] to a "usable" value, collapsing the various
 * sentinel encodings the platform uses for "no specific SIM" / "default" into a
 * single canonical -1.
 */
fun normalizeSubscriptionId(subscriptionId: Int): Int =
    if (subscriptionId < 0) -1 else subscriptionId

/** True when [subscriptionId] designates an explicit SIM (not the system default). */
fun isExplicitSubscription(subscriptionId: Int): Boolean = subscriptionId >= 0
