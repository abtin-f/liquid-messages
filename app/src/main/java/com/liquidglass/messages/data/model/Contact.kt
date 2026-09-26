package com.liquidglass.messages.data.model

/**
 * Minimal contact info resolved from ContactsContract for a phone number.
 *
 * @param number    the phone number this contact info is for (as queried)
 * @param name      display name, or null if the number is not in contacts
 * @param photoUri  contact photo content uri string, or null
 */
@androidx.compose.runtime.Immutable
data class Contact(
    val number: String,
    val name: String? = null,
    val photoUri: String? = null
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: number
}
