package de.haberland.meiocrworkout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.haberland.meiocrworkout.R
import de.haberland.meiocrworkout.domain.model.WorkoutRecord
import de.haberland.meiocrworkout.ui.buildRoundLine
import de.haberland.meiocrworkout.ui.displayName
import de.haberland.meiocrworkout.util.formatDistance
import de.haberland.meiocrworkout.util.formatDuration

@Composable
fun SummaryScreen(
    records: List<WorkoutRecord>,
    onDone: () -> Unit,
) {
    if (records.isEmpty()) return

    var selectedIndex by remember(records) { mutableIntStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, records.lastIndex)
    val record = records[safeIndex]
    val isGroup = records.size > 1 || record.groupId != null

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                if (record.aborted) {
                    stringResource(R.string.summary_title_aborted)
                } else {
                    stringResource(R.string.summary_title_completed)
                },
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(
                    R.string.summary_profile_mode_line,
                    record.profileName,
                    record.mode.displayName(),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isGroup) {
            item {
                ScrollableTabRow(
                    selectedTabIndex = safeIndex,
                    edgePadding = 0.dp,
                ) {
                    records.forEachIndexed { index, item ->
                        val color = GROUP_COLORS[(item.participantColorIndex ?: index) % GROUP_COLORS.size]
                        Tab(
                            selected = safeIndex == index,
                            onClick = { selectedIndex = index },
                            text = {
                                Text(
                                    text = item.participantName ?: stringResource(
                                        R.string.group_participant_default,
                                        index + 1,
                                    ),
                                    color = if (safeIndex == index) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (safeIndex == index) FontWeight.Black else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isGroup) {
                        SummaryValue(
                            stringResource(R.string.label_participant),
                            record.participantName ?: stringResource(
                                R.string.group_participant_default,
                                safeIndex + 1,
                            ),
                        )
                    }
                    SummaryValue(stringResource(R.string.label_duration), formatDuration(record.durationMs))
                    SummaryValue(stringResource(R.string.mode_rounds), record.completedRounds.toString())
                    SummaryValue(
                        stringResource(R.string.label_distance_traveled),
                        formatDistance(record.completedDistanceMeters),
                    )
                    SummaryValue(stringResource(R.string.label_target), record.targetLabel)
                }
            }
        }

        item {
            Text(
                stringResource(R.string.mode_rounds),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        items(record.rounds) { round ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(
                            R.string.round_summary_line,
                            round.number,
                            formatDuration(round.durationMs),
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        buildRoundLine(
                            round.distanceMeters,
                            round.routeLoadName,
                            round.routeLoadDetail,
                            round.obstacleName,
                            round.obstacleDetail,
                        ),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.action_done), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, fontWeight = FontWeight.Bold)
    }
}
