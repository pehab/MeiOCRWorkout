package de.haberland.meiocrworkout.util

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import java.text.DateFormat
import java.util.Date

/**
 * Pure, UI-independent formatting helpers shared by the workout, history and
 * summary screens. Kept free of Compose imports so they stay trivially unit-testable.
 *
 * Only numbers/units here on purpose (durations, metric distances, dates) - none of
 * them contain translatable words, so none of this needs string resources. Anything
 * that does contain words lives elsewhere: UiStrings.kt (buildRoundLine, displayName,
 * label - Compose-only, resolved fresh at render time from stringResource) or
 * AppViewModel's targetLabel (resolved once via Context.getString() because it runs
 * outside Compose and gets persisted into a WorkoutRecord).
 */

internal fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

internal fun formatDistance(meters: Int): String = if (meters >= 1000) {
    val km = meters / 1000.0
    if (meters % 1000 == 0) "${meters / 1000} km" else "%.1f km".format(km)
} else {
    "$meters m"
}

internal fun formatDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
