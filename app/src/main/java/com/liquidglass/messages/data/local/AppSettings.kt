package com.liquidglass.messages.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Colour of the user's own (sent) bubbles. */
enum class BubbleStyle(val label: String) {
    /** iMessage blue. */
    BLUE("Blue"),
    /** iOS "Text Message" (SMS) green. */
    GREEN("Green"),
    PURPLE("Purple"),
    PINK("Pink"),
    ORANGE("Orange"),
    TEAL("Teal"),
    GRAPHITE("Graphite"),
}

/** iOS Display & Brightness › Appearance. */
enum class AppearanceMode(val label: String) { SYSTEM("Automatic"), LIGHT("Light"), DARK("Dark") }

/** iOS 26 Display › Liquid Glass: see-through or frosted/tinted. */
enum class GlassLook(val label: String) { CLEAR("Clear"), TINTED("Tinted") }

/** Dynamic Type steps shown on the iOS Text Size slider. */
enum class TextSize(val label: String, val scale: Float) {
    XSMALL("Extra Small", 0.86f),
    SMALL("Small", 0.93f),
    DEFAULT("Default", 1f),
    LARGE("Large", 1.1f),
    XLARGE("Extra Large", 1.2f),
    XXLARGE("Huge", 1.32f),
}

/** Background drawn behind a conversation. */
enum class ChatWallpaper(val label: String) {
    NONE("None"), SKY("Sky"), SUNSET("Sunset"), MINT("Mint"), LAVENDER("Lavender"), MIDNIGHT("Midnight"),
    WATER("Water"), AURORA("Aurora"),
}

/** iOS Notifications › Show Previews. */
enum class NotificationPreviews(val label: String) {
    ALWAYS("Always"), WHEN_UNLOCKED("When Unlocked"), NEVER("Never"),
}

/** iOS Settings › Messages › Keep Messages. */
enum class KeepMessages(val label: String, val days: Int?) {
    FOREVER("Forever", null), ONE_YEAR("1 Year", 365), THIRTY_DAYS("30 Days", 30),
}

/**
 * App-wide user preferences, persisted in SharedPreferences and exposed as
 * [StateFlow]s so the theme re-renders the moment a setting changes.
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> choice(key: String, default: E): MutableStateFlow<E> =
        MutableStateFlow(
            prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
        )

    private fun <E : Enum<E>> saveChoice(key: String, value: E, flow: MutableStateFlow<E>) {
        prefs.edit().putString(key, value.name).apply()
        flow.value = value
    }

    private fun flag(key: String, default: Boolean) = MutableStateFlow(prefs.getBoolean(key, default))

    private fun save(key: String, value: Boolean, flow: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, value).apply()
        flow.value = value
    }

    // ---- Appearance ----

    private val _bubbleStyle = choice(KEY_BUBBLE, BubbleStyle.BLUE)
    val bubbleStyle: StateFlow<BubbleStyle> = _bubbleStyle.asStateFlow()
    fun setBubbleStyle(style: BubbleStyle) = saveChoice(KEY_BUBBLE, style, _bubbleStyle)

    private val _appearance = choice(KEY_APPEARANCE, AppearanceMode.SYSTEM)
    val appearance: StateFlow<AppearanceMode> = _appearance.asStateFlow()
    fun setAppearance(mode: AppearanceMode) = saveChoice(KEY_APPEARANCE, mode, _appearance)

    private val _glassLook = choice(KEY_GLASS, GlassLook.CLEAR)
    val glassLook: StateFlow<GlassLook> = _glassLook.asStateFlow()
    fun setGlassLook(look: GlassLook) = saveChoice(KEY_GLASS, look, _glassLook)

    private val _textSize = choice(KEY_TEXT_SIZE, TextSize.DEFAULT)
    val textSize: StateFlow<TextSize> = _textSize.asStateFlow()
    fun setTextSize(size: TextSize) = saveChoice(KEY_TEXT_SIZE, size, _textSize)

    private val _wallpaper = choice(KEY_WALLPAPER, ChatWallpaper.NONE)
    val wallpaper: StateFlow<ChatWallpaper> = _wallpaper.asStateFlow()
    fun setWallpaper(w: ChatWallpaper) = saveChoice(KEY_WALLPAPER, w, _wallpaper)

    // ---- Messages ----

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

    private val _autoPlayEffects = flag(KEY_AUTOPLAY, true)
    /** iOS Accessibility › Motion › Auto-Play Message Effects. */
    val autoPlayEffects: StateFlow<Boolean> = _autoPlayEffects.asStateFlow()
    fun setAutoPlayEffects(on: Boolean) = save(KEY_AUTOPLAY, on, _autoPlayEffects)

    private val _swipeToReply = flag(KEY_SWIPE_REPLY, true)
    val swipeToReply: StateFlow<Boolean> = _swipeToReply.asStateFlow()
    fun setSwipeToReply(on: Boolean) = save(KEY_SWIPE_REPLY, on, _swipeToReply)

    private val _lowQualityImages = flag(KEY_LOW_QUALITY, false)
    /** iOS "Low Quality Image Mode": smaller photos over MMS. */
    val lowQualityImages: StateFlow<Boolean> = _lowQualityImages.asStateFlow()
    fun setLowQualityImages(on: Boolean) = save(KEY_LOW_QUALITY, on, _lowQualityImages)

    private val _filterUnknown = flag(KEY_FILTER_UNKNOWN, false)
    /** iOS "Filter Unknown Senders": adds Known/Unknown filters to the inbox. */
    val filterUnknown: StateFlow<Boolean> = _filterUnknown.asStateFlow()
    fun setFilterUnknown(on: Boolean) = save(KEY_FILTER_UNKNOWN, on, _filterUnknown)

    private val _keepMessages = choice(KEY_KEEP, KeepMessages.FOREVER)
    val keepMessages: StateFlow<KeepMessages> = _keepMessages.asStateFlow()
    fun setKeepMessages(k: KeepMessages) = saveChoice(KEY_KEEP, k, _keepMessages)

    // ---- Notifications & feedback ----

    private val _previews = choice(KEY_PREVIEWS, NotificationPreviews.ALWAYS)
    val notificationPreviews: StateFlow<NotificationPreviews> = _previews.asStateFlow()
    fun setNotificationPreviews(p: NotificationPreviews) = saveChoice(KEY_PREVIEWS, p, _previews)

    private val _haptics = flag(KEY_HAPTICS, true)
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()
    fun setHaptics(on: Boolean) = save(KEY_HAPTICS, on, _haptics)

    private val _sendSound = flag(KEY_SEND_SOUND, true)
    /** The iOS "whoosh" when a message goes out. */
    val sendSound: StateFlow<Boolean> = _sendSound.asStateFlow()
    fun setSendSound(on: Boolean) = save(KEY_SEND_SOUND, on, _sendSound)

    private companion object {
        const val PREFS = "app_settings"
        const val KEY_BUBBLE = "bubble_style"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_GLASS = "glass_look"
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_WALLPAPER = "wallpaper"
        const val KEY_DELIVERY = "delivery_reports"
        const val KEY_CHAR_COUNT = "character_count"
        const val KEY_PHOTOS = "show_contact_photos"
        const val KEY_AUTOPLAY = "autoplay_effects"
        const val KEY_SWIPE_REPLY = "swipe_to_reply"
        const val KEY_LOW_QUALITY = "low_quality_images"
        const val KEY_FILTER_UNKNOWN = "filter_unknown"
        const val KEY_KEEP = "keep_messages"
        const val KEY_PREVIEWS = "notification_previews"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SEND_SOUND = "send_sound"
    }
}
