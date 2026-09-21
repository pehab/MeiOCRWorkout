package de.haberland.meiocrworkout

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattingTest {

    // --- formatDuration ---

    @Test
    fun formatDurationHandlesZero() {
        assertEquals("00:00", formatDuration(0L))
    }

    @Test
    fun formatDurationHandlesSecondsUnderAMinute() {
        assertEquals("00:05", formatDuration(5_000L))
    }

    @Test
    fun formatDurationHandlesExactlyOneMinute() {
        assertEquals("01:00", formatDuration(60_000L))
    }

    @Test
    fun formatDurationHandlesMinutesAndSeconds() {
        // 12 * 60 + 34 = 754 seconds
        assertEquals("12:34", formatDuration(754_000L))
    }

    @Test
    fun formatDurationSwitchesToHoursMinutesSecondsPastOneHour() {
        // 1h 01m 01s
        assertEquals("1:01:01", formatDuration(3_661_000L))
    }

    @Test
    fun formatDurationTreatsNegativeValuesAsZero() {
        // coerceAtLeast(0) inside formatDuration: defensive against a clock/lap-time
        // edge case producing a negative delta, should never render a negative time.
        assertEquals("00:00", formatDuration(-500L))
    }

    // --- formatDistance ---

    @Test
    fun formatDistanceStaysInMetersBelowOneKilometer() {
        assertEquals("250 m", formatDistance(250))
        assertEquals("0 m", formatDistance(0))
        assertEquals("999 m", formatDistance(999))
    }

    @Test
    fun formatDistanceSwitchesToWholeKilometersOnExactThousands() {
        assertEquals("1 km", formatDistance(1000))
        assertEquals("2 km", formatDistance(2000))
        assertEquals("10 km", formatDistance(10_000))
    }

    @Test
    fun formatDistanceUsesOneDecimalForNonExactKilometers() {
        // "%.1f".format(...) formats with the JVM DEFAULT locale's decimal separator
        // (comma in German, period in English) - genuinely locale-sensitive, not a
        // test artifact. Pinned to Locale.US here so this assertion is deterministic
        // regardless of which machine/CI runs it; on a real German-locale device this
        // same call renders "1,5 km". Worth knowing now that the app is bilingual -
        // this one line follows the DEVICE'S region setting, not the chosen app
        // language, so an English-app-language override on a German-region device
        // would still show a comma here. Not fixed here since it's a legitimate open
        // design question (matches regional convention vs. matches app language),
        // not an obvious bug - flagging it is the point of this test.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("1.5 km", formatDistance(1500))
            assertEquals("2.3 km", formatDistance(2300))
        } finally {
            Locale.setDefault(original)
        }
    }

    // --- formatDate ---
    // DateFormat.getDateTimeInstance(...) is locale- AND timezone-dependent by design
    // (that's the whole point of using it instead of hand-rolling date formatting), so
    // these are smoke tests for "does it produce plausible, distinct output" rather than
    // assertions on an exact string.

    @Test
    fun formatDateProducesNonBlankOutput() {
        assertTrue(formatDate(0L).isNotBlank())
    }

    @Test
    fun formatDateDiffersForDifferentInstants() {
        val a = formatDate(0L)
        val b = formatDate(1_000L * 60 * 60 * 24 * 365 * 10) // roughly ten years later
        assertNotEquals(a, b)
    }
}
