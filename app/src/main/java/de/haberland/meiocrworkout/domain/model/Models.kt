package de.haberland.meiocrworkout.domain.model

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import java.util.UUID

data class WeightedItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val weight: Int = 1,
    val enabled: Boolean = true,
    val detail: String = "",
    val isNone: Boolean = false
)

data class DistanceOption(
    val id: String = UUID.randomUUID().toString(),
    val oneWayMeters: Int,
    val weight: Int = 1,
    val enabled: Boolean = true
) {
    val totalMeters: Int get() = oneWayMeters * 2
    // Display label moved to DistanceOption.label() in UiStrings.kt - it needs
    // stringResource(), so it can't stay a plain property on this data class.
}

data class WorkoutProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val distances: List<DistanceOption> = emptyList(),
    val routeLoads: List<WeightedItem> = emptyList(),
    val obstacles: List<WeightedItem> = emptyList()
)

data class WorkoutRound(
    val number: Int,
    val distance: DistanceOption?,
    val routeLoad: WeightedItem?,
    val obstacle: WeightedItem?
) {
    val totalMeters: Int get() = distance?.totalMeters ?: 0
    val isEmpty: Boolean get() = totalMeters == 0 && routeLoad == null && obstacle == null
}

enum class WorkoutMode {
    ROUNDS,
    DISTANCE,
    AMRAP_TIME,
    AMRAP_OPEN
    // Display name moved to WorkoutMode.displayName() in UiStrings.kt - same reason
    // as DistanceOption.label() above.
}

data class WorkoutPlan(
    val profileId: String,
    val profileName: String,
    val mode: WorkoutMode,
    val rounds: List<WorkoutRound>,
    val requestedRounds: Int? = null,
    val requestedDistanceMeters: Int? = null,
    val requestedDurationMinutes: Int? = null
) {
    val plannedDistanceMeters: Int get() = rounds.sumOf { it.totalMeters }
    val isOpenEnded: Boolean get() = mode == WorkoutMode.AMRAP_TIME || mode == WorkoutMode.AMRAP_OPEN
}

data class WorkoutRoundRecord(
    val number: Int,
    val distanceOneWayMeters: Int,
    val routeLoadName: String?,
    val routeLoadDetail: String?,
    val obstacleName: String?,
    val obstacleDetail: String?,
    val durationMs: Long
) {
    val totalMeters: Int get() = distanceOneWayMeters * 2
}

data class WorkoutRecord(
    val id: String = UUID.randomUUID().toString(),
    val startedAtEpochMs: Long,
    val profileName: String,
    val mode: WorkoutMode,
    val targetLabel: String,
    val durationMs: Long,
    val aborted: Boolean,
    val rounds: List<WorkoutRoundRecord>
) {
    val completedRounds: Int get() = rounds.size
    val completedDistanceMeters: Int get() = rounds.sumOf { it.totalMeters }
}

data class WorkoutResult(
    val lapTimes: List<Long>,
    val durationMs: Long,
    val startedAtEpochMs: Long,
    val aborted: Boolean
)
