package de.haberland.meiocrworkout.ui.screens

import de.haberland.meiocrworkout.R
import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

@Composable
fun WorkoutScreen(
    plan: WorkoutPlan,
    onFinish: (WorkoutResult) -> Unit,
    onCancelWithoutRounds: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    val startedAtEpoch = remember { System.currentTimeMillis() }
    val start = remember { SystemClock.elapsedRealtime() }
    var lastLap by remember { mutableLongStateOf(start) }
    var laps by remember { mutableStateOf<List<Long>>(emptyList()) }
    var now by remember { mutableLongStateOf(start) }
    var showFinishDialog by remember { mutableStateOf(false) }

    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(300)
        }
    }

    val round = plan.rounds[index]
    val elapsed = now - start
    val targetMs = plan.requestedDurationMinutes?.times(60_000L)
    val timeUp = plan.mode == WorkoutMode.AMRAP_TIME && targetMs != null && elapsed >= targetMs
    val tapSource = remember { MutableInteractionSource() }

    fun finish(updatedLaps: List<Long>, aborted: Boolean) {
        val end = SystemClock.elapsedRealtime()
        onFinish(
            WorkoutResult(
                lapTimes = updatedLaps,
                durationMs = end - start,
                startedAtEpochMs = startedAtEpoch,
                aborted = aborted
            )
        )
    }

    fun advanceRound() {
        val t = SystemClock.elapsedRealtime()
        val updated = laps + (t - lastLap)
        laps = updated
        lastLap = t

        // Note: an AMRAP offen plan is not literally infinite - SessionGenerator
        // pre-generates Limits.AMRAP_ROUND_BUFFER rounds. Tapping through all of
        // them (extremely unlikely, but possible) falls into the `else` branch
        // below and ends the workout rather than looping forever.
        when {
            plan.mode == WorkoutMode.AMRAP_TIME && timeUp -> finish(updated, aborted = false)
            !plan.isOpenEnded && index == plan.rounds.lastIndex -> finish(updated, aborted = false)
            index < plan.rounds.lastIndex -> index++
            else -> finish(updated, aborted = false)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = tapSource, indication = null, onClick = ::advanceRound)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.profileName.uppercase(), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    Text(
                        when (plan.mode) {
                            WorkoutMode.ROUNDS, WorkoutMode.DISTANCE -> stringResource(R.string.workout_round_of, index + 1, plan.rounds.size)
                            WorkoutMode.AMRAP_TIME, WorkoutMode.AMRAP_OPEN -> stringResource(R.string.workout_round_open, index + 1)
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.workout_total_time_label),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    formatDuration(elapsed),
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                if (plan.mode == WorkoutMode.AMRAP_TIME && targetMs != null) {
                    val remaining = (targetMs - elapsed).coerceAtLeast(0L)
                    Text(
                        if (timeUp) stringResource(R.string.workout_time_up) else stringResource(R.string.workout_time_remaining, formatDuration(remaining)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (timeUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.workout_distance_label), fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    if ((round.distance?.totalMeters ?: 0) == 0) {
                        Text(stringResource(R.string.workout_direct), fontSize = 62.sp, fontWeight = FontWeight.Black)
                        Text(stringResource(R.string.workout_to_obstacle), fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("${round.distance!!.totalMeters} m", fontSize = 78.sp, lineHeight = 80.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }
                }

                Box(
                    Modifier.fillMaxWidth().background(Color(0xFF1A1E21), RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.workout_load_label), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text(
                            (round.routeLoad?.name ?: stringResource(R.string.workout_load_none)).uppercase(),
                            fontSize = 38.sp,
                            lineHeight = 41.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        round.routeLoad?.detail?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.workout_next_obstacle_label), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text(
                        (round.obstacle?.name ?: stringResource(R.string.workout_obstacle_none)).uppercase(),
                        fontSize = 52.sp,
                        lineHeight = 54.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    round.obstacle?.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }

            Text(
                when {
                    plan.mode == WorkoutMode.AMRAP_TIME && timeUp -> stringResource(R.string.workout_hint_time_up)
                    !plan.isOpenEnded && index == plan.rounds.lastIndex -> stringResource(R.string.workout_hint_last_round)
                    else -> stringResource(R.string.workout_hint_next_round)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (timeUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            OutlinedButton(
                onClick = { showFinishDialog = true },
                modifier = Modifier.fillMaxWidth(0.58f).height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(stringResource(R.string.action_finish_training), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(R.string.dialog_finish_title)) },
            text = { Text(stringResource(R.string.dialog_finish_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    if (laps.isEmpty()) onCancelWithoutRounds() else finish(laps, aborted = true)
                }) { Text(stringResource(R.string.action_finish_caps)) }
            },
            dismissButton = { TextButton(onClick = { showFinishDialog = false }) { Text(stringResource(R.string.action_continue_caps)) } }
        )
    }
}
