package com.sirelon.sellsnap.features.seller.messages.domain

private val WallClock = Regex("""\d{4}-(\d{2})-(\d{2}) (\d{2}:\d{2}):\d{2}""")

/**
 * Shows an OLX timestamp such as `2026-10-30 21:33:53` as `30.10 21:33`.
 *
 * Inferred: OLX returns the market's local wall-clock time without an offset (SIR-88, olx.ua), so
 * the text is trimmed rather than converted to the device's zone. Any other format is returned
 * unchanged.
 */
fun formatOlxWallClock(raw: String): String {
    val parts = WallClock.matchEntire(raw)?.groupValues ?: return raw
    return "${parts[2]}.${parts[1]} ${parts[3]}"
}
