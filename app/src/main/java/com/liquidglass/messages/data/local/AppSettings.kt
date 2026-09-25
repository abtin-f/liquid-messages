package com.liquidglass.messages.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Colour of the user's own (sent) bubbles. */
enum class BubbleStyle {
    /** iMessage blue. */
    BLUE,
    /** iOS "Text Message" (SMS) green. */
    GREEN,
}

/**
 * App-wide user preferences, persisted in SharedPreferences and exposed as
 * [StateFlow]s so the theme re-renders the moment a setting changes.
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _bubbleStyle = MutableStateFlow(
        runCatching { BubbleStyle.valueOf(prefs.getString(KEY_BUBBLE, null) ?: "") }
            .getOrDefault(BubbleStyle.BLUE)
    )
    val bubbleStyle: StateFlow<BubbleStyle> = _bubbleStyle.asStateFlow()

    fun setBubbleStyle(style: BubbleStyle) {
        prefs.edit().putString(KEY_BUBBLE, style.name).apply()
        _bubbleStyle.value = style
    }

    private fun flag(key: String, default: Boolean) = MutableStateFlow(prefs.getBoolean(key, default))

    private val _deliveryReports = flag(KEY_DELIVERY, true)
    /** Ask the carrier for delivery reports ("Delivered" under sent messages). */
    val deliveryReports: StateFlow<Boolean> = _deliveryReports.asStateFlow()
    fun setDeliveryReports(on: Boolean) = save(KEY_DELIVERY, on, _deliveryReports)

    private val _characterCount = flag(KEY_CHAR_COUNT, false)
    /** Show "used/160" while typing, like iOS's Character Count setting. */
    val characterCount: StateFlow<Boolean> = _characterCount.asStateFlow()
    fun setCharacterCount(on: Boolean) = save(KEY_CHAR_COUNT, on, _characterCount)

    private val _contactPhotos = flag(KEY_PHOTOS, true)
    /** iOS "Show Contact Photos". */
    val showContactPhotos: StateFlow<Boolean> = _contactPhotos.asStateFlow()
    fun setShowContactPhotos(on: Boolean) = save(KEY_PHOTOS, on, _contactPhotos)

    private fun save(key: String, value: Boolean, flow: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, value).apply()
        flow.value = value
    }

    private companion object {
        const val PREFS = "app_settings"
        const val KEY_BUBBLE = "bubble_style"
        const val KEY_DELIVERY = "delivery_reports"
        const val KEY_CHAR_COUNT = "character_count"
        const val KEY_PHOTOS = "show_contact_photos"
    }
}
