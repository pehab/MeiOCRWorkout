package de.haberland.meiocrworkout.ui.screens

import android.os.SystemClock
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
import de.haberland.meiocrworkout.domain.model.GroupWorkoutSession
import de.haberland.meiocrworkout.domain.model.WorkoutRound
import de.haberland.meiocrworkout.util.formatDuration
import kotlinx.coroutines.delay

@Composable
fun GroupWorkoutScreen(
    session: GroupWorkoutSession,
    onFinish: () -> Unit,
) {
    val started = remember { SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(started) }
    var highlightedId by remember { mutableStateOf<Int?>(null) }
    val indices = remember { mutableStateMapOf<Int, Int>() }
    var showFinishDialog by remember { mutableStateOf(false) }

    session.athletes.forEach { athlete ->
        if (indices[athlete.id] == null) indices[athlete.id] = 0
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
    val targetMs = session.durationMinutes * 60_000L
    val remaining = (targetMs - elapsed).coerceAtLeast(0L)
    val timeUp = elapsed >= targetMs
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
                    text = if (timeUp) stringResource(R.string.workout_time_up) else formatDuration(remaining),
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
                maxWidth < 600.dp -> if (count <= 4) 2 else 3
                count <= 8 -> 4
                count <= 16 -> 4
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
                    AthleteCard(
                        athlete = athlete,
                        index = indices[athlete.id] ?: 0,
                        emphasized = false,
                        modifier = Modifier.height(if (count <= 8) 150.dp else 126.dp),
                        onClick = {
                            val current = indices[athlete.id] ?: 0
                            indices[athlete.id] = (current + 1) % athlete.rounds.size
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
                TextButton(onClick = onFinish) {
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
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val round = athlete.rounds[index % athlete.rounds.size]
    val next = athlete.rounds[(index + 1) % athlete.rounds.size]

    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
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
                    text = "${index + 1}/${athlete.rounds.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }

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
