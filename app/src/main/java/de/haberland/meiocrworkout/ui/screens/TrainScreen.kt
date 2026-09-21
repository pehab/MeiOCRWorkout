package de.haberland.meiocrworkout.ui.screens

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TrainScreen(
    profiles: List<WorkoutProfile>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit,
    onStart: (WorkoutPlan) -> Unit
) {
    var mode by remember { mutableStateOf(WorkoutMode.ROUNDS) }
    var roundsText by remember { mutableStateOf("8") }
    var kmText by remember { mutableStateOf("10") }
    var minutesText by remember { mutableStateOf("60") }
    var profileMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.first()
    val activeDistances = profile.distances.count { it.enabled }
    val activeLoads = profile.routeLoads.count { it.enabled && !it.isNone }
    val activeObstacles = profile.obstacles.count { it.enabled && !it.isNone }

    // Resolved here, during composition, rather than inline inside the onClick below:
    // stringResource() only works while composing, and Button's onClick runs later, at
    // click time, outside composition.
    val invalidRoundsError = stringResource(R.string.error_invalid_rounds)
    val invalidDistanceError = stringResource(R.string.error_invalid_distance)
    val distanceMustBePositiveError = stringResource(R.string.error_distance_must_be_positive)
    val invalidTimeError = stringResource(R.string.error_invalid_time)
    val noUsableItemsError = stringResource(R.string.error_no_usable_items)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Text(stringResource(R.string.app_name), fontSize = 31.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.train_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Text(stringResource(R.string.label_profile), fontWeight = FontWeight.Bold)
            Box {
                OutlinedButton(
                    onClick = { profileMenu = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(profile.name, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                    profiles.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.name) },
                            onClick = {
                                onProfileSelected(item.id)
                                profileMenu = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.profile_summary, activeDistances, activeLoads, activeObstacles),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }

        item {
            Text(stringResource(R.string.label_training_mode), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton(stringResource(R.string.mode_rounds), mode == WorkoutMode.ROUNDS, Modifier.weight(1f)) { mode = WorkoutMode.ROUNDS }
                    ModeButton(stringResource(R.string.mode_distance), mode == WorkoutMode.DISTANCE, Modifier.weight(1f)) { mode = WorkoutMode.DISTANCE }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton(stringResource(R.string.mode_amrap_time), mode == WorkoutMode.AMRAP_TIME, Modifier.weight(1f)) { mode = WorkoutMode.AMRAP_TIME }
                    ModeButton(stringResource(R.string.mode_amrap_open), mode == WorkoutMode.AMRAP_OPEN, Modifier.weight(1f)) { mode = WorkoutMode.AMRAP_OPEN }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (mode) {
                        WorkoutMode.ROUNDS -> {
                            Text(stringResource(R.string.label_rounds_count), fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                roundsText,
                                { roundsText = it.filter(Char::isDigit).take(3) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                suffix = { Text(stringResource(R.string.mode_rounds)) },
                                singleLine = true
                            )
                        }
                        WorkoutMode.DISTANCE -> {
                            Text(stringResource(R.string.label_target_distance), fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                kmText,
                                { kmText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                suffix = { Text(stringResource(R.string.suffix_km)) },
                                singleLine = true
                            )
                        }
                        WorkoutMode.AMRAP_TIME -> {
                            Text(stringResource(R.string.label_target_time), fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                minutesText,
                                { minutesText = it.filter(Char::isDigit).take(3) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                suffix = { Text(stringResource(R.string.suffix_minutes)) },
                                singleLine = true
                            )
                        }
                        WorkoutMode.AMRAP_OPEN -> {
                            Text(stringResource(R.string.label_amrap_open), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.amrap_open_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            error = null
                            runCatching {
                                when (mode) {
                                    WorkoutMode.ROUNDS -> {
                                        val count = roundsText.toIntOrNull()
                                            ?.coerceIn(Limits.ROUNDS_MIN, Limits.ROUNDS_MAX)
                                            ?: error(invalidRoundsError)
                                        SessionGenerator.byRounds(profile, count)
                                    }
                                    WorkoutMode.DISTANCE -> {
                                        val km = kmText.replace(',', '.').toDoubleOrNull()
                                            ?: error(invalidDistanceError)
                                        val meters = (km * 1000).toInt()
                                        require(meters > 0) { distanceMustBePositiveError }
                                        SessionGenerator.byDistance(profile, meters)
                                    }
                                    WorkoutMode.AMRAP_TIME -> {
                                        val minutes = minutesText.toIntOrNull()
                                            ?.coerceIn(Limits.AMRAP_MINUTES_MIN, Limits.AMRAP_MINUTES_MAX)
                                            ?: error(invalidTimeError)
                                        SessionGenerator.byAmrapTime(profile, minutes)
                                    }
                                    WorkoutMode.AMRAP_OPEN -> SessionGenerator.byAmrapOpen(profile)
                                }
                            }.onSuccess(onStart).onFailure {
                                error = it.message ?: noUsableItemsError
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    ) {
                        Text(stringResource(R.string.action_start), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        item {
            Text(
                stringResource(R.string.train_footer_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(48.dp)) { Text(label, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp)) { Text(label) }
    }
}
