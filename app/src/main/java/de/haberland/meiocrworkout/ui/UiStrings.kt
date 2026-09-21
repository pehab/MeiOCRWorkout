package de.haberland.meiocrworkout.ui

import de.haberland.meiocrworkout.R
import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Display-string helpers that need [stringResource] and therefore only work from a
 * @Composable context - kept separate from Formatting.kt, whose functions are plain
 * and unit-testable on purpose. Used from HistoryScreen and SummaryScreen, both of
 * which render live from already-persisted structured data, so recomputing the text
 * from the current locale on every recomposition is exactly right - contrast with
 * AppViewModel's targetLabel(), which runs outside Compose and gets baked into a
 * WorkoutRecord at save time (see the comment there for why that one is different).
 */

@Composable
fun WorkoutMode.displayName(): String = stringResource(
    when (this) {
        WorkoutMode.ROUNDS -> R.string.mode_rounds
        WorkoutMode.DISTANCE -> R.string.mode_distance
        WorkoutMode.AMRAP_TIME -> R.string.mode_amrap_time
        WorkoutMode.AMRAP_OPEN -> R.string.mode_amrap_open
    }
)

@Composable
fun DistanceOption.label(): String = if (totalMeters == 0) {
    stringResource(R.string.distance_label_none)
} else {
    stringResource(R.string.distance_label_total, totalMeters)
}

@Composable
fun buildRoundLine(
    oneWayMeters: Int,
    routeName: String?,
    routeDetail: String?,
    obstacleName: String?,
    obstacleDetail: String?
): String {
    val distance = if (oneWayMeters == 0) {
        stringResource(R.string.round_distance_direct)
    } else {
        stringResource(R.string.round_distance_total, oneWayMeters)
    }
    val load = routeName?.let {
        if (routeDetail.isNullOrBlank()) it else stringResource(R.string.round_item_with_detail, it, routeDetail)
    } ?: stringResource(R.string.round_no_load)
    val obstacle = obstacleName?.let {
        if (obstacleDetail.isNullOrBlank()) it else stringResource(R.string.round_item_with_detail, it, obstacleDetail)
    } ?: stringResource(R.string.round_no_obstacle)
    return stringResource(R.string.round_line_format, distance, load, obstacle)
}
