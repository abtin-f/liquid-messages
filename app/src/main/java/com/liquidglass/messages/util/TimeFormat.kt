package com.liquidglass.messages.util

import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * iMessage-style relative time formatting, used by both the conversation list
 * and the chat date separators.
 */
object TimeFormat {

    /** Compact stamp for the conversation list row (e.g. "9:41 AM", "Yesterday", "Mon", "5/14/24"). */
    fun conversationStamp(context: Context, millis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }

        return when {
            isSameDay(now, then) -> timeOnly(context, millis)
            isYesterday(now, then) -> "Yesterday"
            withinDays(now, then, 7) -> dayOfWeek(millis)
            isSameYear(now, then) -> shortDate(context, millis)
            else -> shortDateWithYear(context, millis)
        }
    }

    /** Full separator label shown between message groups (e.g. "Today 9:41 AM"). */
    fun messageSeparator(context: Context, millis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val time = timeOnly(context, millis)
        return when {
            isSameDay(now, then) -> "Today $time"
            isYesterday(now, then) -> "Yesterday $time"
            withinDays(now, then, 7) -> "${dayOfWeek(millis)} $time"
            isSameYear(now, then) -> "${shortDate(context, millis)} $time"
            else -> "${shortDateWithYear(context, millis)} $time"
        }
    }

    /**
     * The two halves of an iOS timestamp header — the bold day part and the
     * regular time part: ("Today", "9:41 AM"), ("Mon", "9:41 AM"), ("Sep 3", …).
     */
    fun messageSeparatorParts(context: Context, millis: Long): Pair<String, String> {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val time = timeOnly(context, millis)
        val day = when {
            isSameDay(now, then) -> "Today"
            isYesterday(now, then) -> "Yesterday"
            withinDays(now, then, 7) -> DateFormat.format("EEEE", Date(millis)).toString()
            isSameYear(now, then) -> DateFormat.format("EEE, MMM d", Date(millis)).toString()
            else -> DateFormat.format("MMM d, yyyy", Date(millis)).toString()
        }
        return day to time
    }

    /** iOS Send Later pill: "Today at 9:41 PM", "Tomorrow at 9:00 AM", "Fri, Oct 3 at 8:00 AM". */
    fun sendLaterLabel(context: Context, millis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val time = timeOnly(context, millis)
        return when {
            isSameDay(now, then) -> "Today at $time"
            isSameDay(tomorrow, then) -> "Tomorrow at $time"
            else -> "${DateFormat.format("EEE, MMM d", Date(millis))} at $time"
        }
    }

    /** "9:41 AM" / "21:41" depending on the device's 12/24h setting. */
    fun timeOnly(context: Context, millis: Long): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a"
        return DateFormat.format(pattern, Date(millis)).toString()
    }

    private fun dayOfWeek(millis: Long): String =
        DateFormat.format("EEE", Date(millis)).toString()

    private fun shortDate(context: Context, millis: Long): String =
        DateFormat.format("M/d/yy", Date(millis)).toString()

    private fun shortDateWithYear(context: Context, millis: Long): String =
        DateFormat.format("M/d/yy", Date(millis)).toString()

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isSameYear(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR)

    private fun isYesterday(now: Calendar, then: Calendar): Boolean {
        val y = now.clone() as Calendar
        y.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(y, then)
    }

    private fun withinDays(now: Calendar, then: Calendar, days: Int): Boolean {
        val diff = now.timeInMillis - then.timeInMillis
        return diff in 0..(days.toLong() * 24 * 60 * 60 * 1000)
    }
}
