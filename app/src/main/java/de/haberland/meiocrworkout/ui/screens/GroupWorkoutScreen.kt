package de.haberland.meiocrworkout.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.haberland.meiocrworkout.R
import de.haberland.meiocrworkout.domain.model.GroupAthletePlan
import de.haberland.meiocrworkout.domain.model.GroupParticipantWorkoutResult
import de.haberland.meiocrworkout.domain.model.GroupWorkoutResult
import de.haberland.meiocrworkout.domain.model.GroupWorkoutSession
import de.haberland.meiocrworkout.domain.model.WorkoutMode
import de.haberland.meiocrworkout.domain.model.WorkoutRound
import de.haberland.meiocrworkout.util.formatDuration
import kotlinx.coroutines.delay

@Composable
fun GroupWorkoutScreen(
    session: GroupWorkoutSession,
    onFinish: (GroupWorkoutResult) -> Unit,
) {
    val startedAtEpochMs = remember { System.currentTimeMillis() }
    val started = remember { SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(started) }
    var highlightedId by remember { mutableStateOf<Int?>(null) }
    val indices = remember { mutableStateMapOf<Int, Int>() }
    val lastLapTimes = remember { mutableStateMapOf<Int, Long>() }
    val laps = remember { mutableStateMapOf<Int, List<Long>>() }
    var showFinishDialog by remember { mutableStateOf(false) }

    session.athletes.forEach { athlete ->
        if (indices[athlete.id] == null) indices[athlete.id] = 0
        if (lastLapTimes[athlete.id] == null) lastLapTimes[athlete.id] = started
        if (laps[athlete.id] == null) laps[athlete.id] = emptyList()
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(300)
        }
    }

    LaunchedEffect(highlightedId) {
        if (highlightedId != null) {
            delay(1400)
            highlightedId = null
        }
    }

    val elapsed = now - started
    val targetMs = session.plan.requestedDurationMinutes?.times(60_000L)
    val timeUp = session.plan.mode == WorkoutMode.AMRAP_TIME &&
        targetMs != null &&
        elapsed >= targetMs
    val highlighted = session.athletes.firstOrNull { it.id == highlightedId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.profileName.uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                )
                Text(
                    text = stringResource(R.string.group_training_title),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        timeUp -> stringResource(R.string.workout_time_up)
                        session.plan.mode == WorkoutMode.AMRAP_TIME && targetMs != null ->
                            formatDuration((targetMs - elapsed).coerceAtLeast(0L))
                        else -> formatDuration(elapsed)
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = if (timeUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.group_participants_count, session.athletes.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        highlighted?.let { athlete ->
            AthleteCard(
                athlete = athlete,
                index = indices[athlete.id] ?: 0,
                openEnded = session.plan.isOpenEnded,
                emphasized = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                onClick = {},
            )
        }

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val count = session.athletes.size
            val columns = when {
                maxWidth < 700.dp -> 2
                maxWidth < 1000.dp -> if (count <= 8) 3 else 4
                count <= 12 -> 4
                else -> 5
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = session.athletes,
                    key = { it.id },
                ) { athlete ->
                    val current = indices[athlete.id] ?: 0
                    AthleteCard(
                        athlete = athlete,
                        index = current,
                        openEnded = session.plan.isOpenEnded,
                        emphasized = false,
                        modifier = Modifier.height(
                            when {
                                maxWidth < 700.dp -> 156.dp
                                count <= 8 -> 150.dp
                                else -> 126.dp
                            }
                        ),
                        onClick = {
                            val tapTime = SystemClock.elapsedRealtime()
                            val lastLap = lastLapTimes[athlete.id] ?: started
                            laps[athlete.id] = (laps[athlete.id] ?: emptyList()) + (tapTime - lastLap)
                            lastLapTimes[athlete.id] = tapTime

                            val nextIndex = if (session.plan.isOpenEnded) {
                                (current + 1) % athlete.rounds.size
                            } else {
                                (current + 1).coerceAtMost(athlete.rounds.size)
                            }
                            indices[athlete.id] = nextIndex
                            highlightedId = athlete.id
                        },
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { showFinishDialog = true },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.45f),
        ) {
            Text(stringResource(R.string.action_finish_training))
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(R.string.dialog_finish_group_title)) },
            text = { Text(stringResource(R.string.dialog_finish_group_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val end = SystemClock.elapsedRealtime()
                        val aborted = when {
                            session.plan.mode == WorkoutMode.AMRAP_TIME -> !timeUp
                            session.plan.mode == WorkoutMode.AMRAP_OPEN -> false
                            else -> session.athletes.any { athlete ->
                                (indices[athlete.id] ?: 0) < athlete.rounds.size
                            }
                        }
                        onFinish(
                            GroupWorkoutResult(
                                startedAtEpochMs = startedAtEpochMs,
                                durationMs = end - started,
                                aborted = aborted,
                                participants = session.athletes.map { athlete ->
                                    GroupParticipantWorkoutResult(
                                        athleteId = athlete.id,
                                        name = athlete.name,
                                        colorIndex = athlete.colorIndex,
                                        lapTimes = laps[athlete.id] ?: emptyList(),
                                    )
                                },
                            )
                        )
                    },
                ) {
                    Text(stringResource(R.string.action_finish_caps))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text(stringResource(R.string.action_continue_caps))
                }
            },
        )
    }
}

@Composable
private fun AthleteCard(
    athlete: GroupAthletePlan,
    index: Int,
    openEnded: Boolean,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val completed = !openEnded && index >= athlete.rounds.size
    val safeIndex = if (completed) athlete.rounds.lastIndex else index % athlete.rounds.size
    val round = athlete.rounds[safeIndex]
    val next = athlete.rounds[(safeIndex + 1) % athlete.rounds.size]
    val athleteColor = GROUP_COLORS[athlete.colorIndex % GROUP_COLORS.size]

    Card(
        modifier = modifier.clickable(enabled = !completed, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                completed -> MaterialTheme.colorScheme.surfaceVariant
                emphasized -> athleteColor.copy(alpha = 0.14f)
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(if (emphasized) 3.dp else 2.dp, athleteColor.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(if (emphasized) 22.dp else 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (emphasized) 16.dp else 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${athlete.id} ${athlete.name}",
                    fontWeight = FontWeight.Black,
                    fontSize = if (emphasized) 20.sp else 15.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (completed) "✓" else "${safeIndex + 1}/${athlete.rounds.size}",
                    color = athleteColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (completed) {
                Text(
                    text = stringResource(R.string.group_completed),
                    fontWeight = FontWeight.Black,
                    fontSize = if (emphasized) 24.sp else 18.sp,
                    color = athleteColor,
                )
            } else {
                Text(
                    text = roundLabel(round),
                    fontWeight = FontWeight.Black,
                    fontSize = if (emphasized) 24.sp else 18.sp,
                    lineHeight = if (emphasized) 26.sp else 20.sp,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                )

                Text(
                    text = stringResource(R.string.group_next, roundLabel(next)),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (emphasized) 14.sp else 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun roundLabel(round: WorkoutRound): String {
    val parts = buildList {
        round.distance?.totalMeters?.let { meters ->
            add(if (meters == 0) stringResource(R.string.workout_direct) else "${meters} m")
        }
        round.routeLoad?.name?.let(::add)
        round.obstacle?.name?.let(::add)
    }
    return parts.joinToString(" · ").ifBlank { stringResource(R.string.workout_obstacle_none) }
}
