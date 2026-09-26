package com.liquidglass.messages.data.sms

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.liquidglass.messages.data.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves phone numbers to device contacts (name + photo) and powers the
 * compose-screen contact search.
 *
 * Reads run on [Dispatchers.IO]; all cursors are closed via [use]. When the
 * READ_CONTACTS permission is missing we degrade gracefully (a bare [Contact]
 * carrying just the number, or an empty search list) rather than throwing.
 */
class ContactsHelper(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * Bounded access-order LRU keyed by the queried number. Successful and
     * "no-match" lookups are both cached so repeated rows don't re-hit the provider;
     * the eldest entry is evicted past [MAX_CACHE] so the cache can't grow without
     * limit. Access is synchronized (it is touched from IO coroutines).
     */
    private val cache = object : LinkedHashMap<String, Contact>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Contact>): Boolean = size > MAX_CACHE
    }

    /**
     * Every phone number in the address book, keyed by its last 10 digits
     * (so "+98 912..." and "0912..." meet). Built with ONE query the first time a
     * lookup needs it, instead of one PhoneLookup query per conversation, which
     * is the difference between an instant inbox and a multi-second one.
     * Dropped whenever the address book changes.
     */
    @Volatile private var index: Map<String, Contact>? = null
    private val indexLock = Any()
    private var observing = false

    private fun key(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    private fun contactIndex(): Map<String, Contact> {
        index?.let { return it }
        synchronized(indexLock) {
            index?.let { return it }
            val map = HashMap<String, Contact>()
            runCatching {
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val number = c.getString(0) ?: continue
                        val k = key(number)
                        if (k.length < 5 || map.containsKey(k)) continue
                        map[k] = Contact(number = number, name = c.getString(1), photoUri = c.getString(2))
                    }
                }
            }
            if (!observing) {
                observing = true
                runCatching {
                    resolver.registerContentObserver(
                        ContactsContract.Contacts.CONTENT_URI, true,
                        object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                            override fun onChange(selfChange: Boolean) = clearCache()
                        },
                    )
                }
            }
            index = map
            return map
        }
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Resolves [number] to a [Contact] via ContactsContract.PhoneLookup.
     *
     * Returns a cached value when present. On a miss, falls back to
     * `Contact(number = number)` (also cached) when the permission is absent or
     * there is no matching contact.
     */
    suspend fun resolveContact(number: String): Contact {
        if (number.isBlank()) return Contact(number = number)

        synchronized(cache) { cache[number] }?.let { return it }

        if (!hasContactsPermission()) {
            val fallback = Contact(number = number)
            synchronized(cache) { cache[number] = fallback }
            return fallback
        }

        val resolved = withContext(Dispatchers.IO) {
            val k = key(number)
            // Fast path: the in-memory address book; short codes use PhoneLookup.
            if (k.length >= 5) contactIndex()[k]?.copy(number = number)
            else queryPhoneLookup(number)
        } ?: Contact(number = number)

        synchronized(cache) { cache[number] = resolved }
        return resolved
    }

    private fun queryPhoneLookup(number: String): Contact? {
        val lookupUri: Uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )

        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI
        )

        return try {
            resolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getStringOrNull(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photo = cursor.getStringOrNull(ContactsContract.PhoneLookup.PHOTO_URI)
                    Contact(number = number, name = name, photoUri = photo)
                } else {
                    null
                }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Searches device contacts by display name OR phone number for the compose
     * screen. Results are distinct by number and capped at [SEARCH_LIMIT].
     *
     * Returns an empty list when [query] is blank or the permission is missing.
     */
    suspend fun searchContacts(query: String): List<Contact> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!hasContactsPermission()) return emptyList()

        return withContext(Dispatchers.IO) {
            queryContacts(trimmed)
        }
    }

    private fun queryContacts(query: String): List<Contact> {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        // Match on either the display name or the (digits of the) number.
        val selection =
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val like = "%$query%"
        val selectionArgs = arrayOf(like, like)
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        val seenNumbers = HashSet<String>()
        val results = ArrayList<Contact>()

        try {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext() && results.size < SEARCH_LIMIT) {
                    val number = cursor.getStringOrNull(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )?.trim().orEmpty()
                    if (number.isEmpty()) continue

                    // Distinct by normalized digits so "+1 555" and "(555)" don't dupe.
                    val key = number.filter { it.isDigit() }.ifEmpty { number }
                    if (!seenNumbers.add(key)) continue

                    val name = cursor.getStringOrNull(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )
                    val photo = cursor.getStringOrNull(
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                    )
                    results.add(Contact(number = number, name = name, photoUri = photo))
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        return results
    }

    /** Drops cached lookups; call after the user grants READ_CONTACTS. */
    fun clearCache() {
        synchronized(cache) { cache.clear() }
        index = null
    }

    companion object {
        private const val SEARCH_LIMIT = 30

        /** Upper bound on the contact-resolution LRU cache. */
        private const val MAX_CACHE = 512
    }
}
