package de.haberland.meiocrworkout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.haberland.meiocrworkout.R
import de.haberland.meiocrworkout.domain.model.WorkoutRecord
import de.haberland.meiocrworkout.ui.buildRoundLine
import de.haberland.meiocrworkout.ui.displayName
import de.haberland.meiocrworkout.util.formatDate
import de.haberland.meiocrworkout.util.formatDistance
import de.haberland.meiocrworkout.util.formatDuration

@Composable
fun HistoryScreen(
    history: List<WorkoutRecord>,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var showClear by remember { mutableStateOf(false) }

    val historyGroups = history
        .groupBy { it.groupId ?: it.id }
        .values
        .toList()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.title_history),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(R.string.history_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (history.isNotEmpty()) {
                TextButton(onClick = { showClear = true }) {
                    Text(stringResource(R.string.action_delete_all))
                }
            }
        }

        if (historyGroups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(
                    items = historyGroups,
                    key = { records -> records.first().groupId ?: records.first().id },
                ) { records ->
                    val key = records.first().groupId ?: records.first().id
                    HistoryCard(
                        records = records,
                        expanded = expandedKey == key,
                        onToggle = {
                            expandedKey = if (expandedKey == key) null else key
                        },
                        onDelete = {
                            records.forEach { onDelete(it.id) }
                        },
                    )
                }
            }
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(stringResource(R.string.dialog_delete_history_title)) },
            text = { Text(stringResource(R.string.dialog_delete_history_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear()
                        showClear = false
                    },
                ) {
                    Text(stringResource(R.string.action_delete_all_caps))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) {
                    Text(stringResource(R.string.action_cancel_caps))
                }
            },
        )
    }
}

@Composable
private fun HistoryCard(
    records: List<WorkoutRecord>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    var selectedIndex by remember(records.map { it.id }) { mutableIntStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, records.lastIndex)
    val record = records[safeIndex]
    val isGroup = records.size > 1 || record.groupId != null

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.profileName, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(
                        formatDate(record.startedAtEpochMs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    if (isGroup) {
                        Text(
                            stringResource(R.string.history_group_label, records.size),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (record.aborted) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.label_aborted)) },
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, stringResource(R.string.cd_delete_training))
                }
            }

            if (isGroup) {
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
                                    item.participantName ?: stringResource(
                                        R.string.group_participant_default,
                                        index + 1,
                                    ),
                                    color = if (safeIndex == index) {
                                        color
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = if (safeIndex == index) {
                                        FontWeight.Black
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                            },
                        )
                    }
                }
            }

            Text(
                stringResource(
                    R.string.history_mode_target_line,
                    record.mode.displayName(),
                    record.targetLabel,
                ),
            )
            Text(
                stringResource(
                    R.string.history_stats_line,
                    record.completedRounds,
                    formatDistance(record.completedDistanceMeters),
                    formatDuration(record.durationMs),
                ),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            TextButton(onClick = onToggle) {
                Text(
                    if (expanded) {
                        stringResource(R.string.action_hide_details)
                    } else {
                        stringResource(R.string.action_show_rounds)
                    },
                )
            }

            if (expanded) {
                HorizontalDivider()
                record.rounds.forEach { round ->
                    Column(Modifier.padding(vertical = 6.dp)) {
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
        }
    }
}
