package com.liquidglass.messages.util

/**
 * Strips spaces, dashes and brackets from a phone number, keeping digits and a
 * leading '+'. Contact numbers like " +91 93705 33275" otherwise reach the
 * Telephony provider unnormalized and can start a second thread for the same
 * person (replies then land in one thread while sends go to the other).
 * E-mail style addresses are left alone.
 */
fun String.toSendableAddress(): String {
    val t = trim()
    if ('@' in t) return t
    val digits = t.filter(Char::isDigit)
    if (digits.isEmpty()) return t
    return if (t.startsWith("+")) "+$digits" else digits
}
