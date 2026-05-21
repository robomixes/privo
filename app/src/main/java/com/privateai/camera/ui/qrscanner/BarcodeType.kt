// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.qrscanner

/**
 * Numeric constants for QR / barcode value types, mirroring the legacy ML Kit
 * `Barcode.TYPE_*` enum values. We use ints (not a Kotlin enum) so existing
 * `QrHistoryItem`s stored on disk — which serialise `valueType` as a raw
 * integer in JSON — remain readable after the ML Kit → ZXing swap.
 *
 * The values match `com.google.mlkit.vision.barcode.common.Barcode.TYPE_*`
 * exactly so the swap is wire-compatible.
 */
object BarcodeType {
    const val UNKNOWN = 0
    const val CONTACT_INFO = 1
    const val EMAIL = 2
    const val ISBN = 3
    const val PHONE = 4
    const val PRODUCT = 5
    const val SMS = 6
    const val TEXT = 7
    const val URL = 8
    const val WIFI = 9
    const val GEO = 10
    const val CALENDAR_EVENT = 11
    const val DRIVER_LICENSE = 12

    /** Symbology constants — only QR code is generated, but reserved for parity. */
    const val FORMAT_QR_CODE = 256

    /**
     * Heuristic mapping from raw decoded text to a value-type constant.
     * Lightweight prefix-based parser; covers the cases the UI uses
     * (icons in QrHistoryTab, action buttons in QrScannerScreen's result
     * sheet). Returns [TEXT] when no specific pattern matches.
     */
    fun classify(raw: String): Int {
        val t = raw.trim()
        return when {
            t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true) -> URL
            t.startsWith("WIFI:", ignoreCase = true) -> WIFI
            t.startsWith("mailto:", ignoreCase = true) ||
            t.startsWith("MATMSG:", ignoreCase = true) -> EMAIL
            t.startsWith("tel:", ignoreCase = true) -> PHONE
            t.startsWith("sms:", ignoreCase = true) ||
            t.startsWith("smsto:", ignoreCase = true) -> SMS
            t.startsWith("geo:", ignoreCase = true) -> GEO
            t.startsWith("BEGIN:VCARD", ignoreCase = true) ||
            t.startsWith("MECARD:", ignoreCase = true) -> CONTACT_INFO
            t.startsWith("BEGIN:VEVENT", ignoreCase = true) ||
            t.startsWith("BEGIN:VCALENDAR", ignoreCase = true) -> CALENDAR_EVENT
            else -> TEXT
        }
    }
}
