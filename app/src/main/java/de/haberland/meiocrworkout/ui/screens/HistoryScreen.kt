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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HistoryScreen(
    history: List<WorkoutRecord>,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    var showClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.title_history), fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.history_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (history.isNotEmpty()) {
                TextButton(onClick = { showClear = true }) { Text(stringResource(R.string.action_delete_all)) }
            }
        }

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(history, key = { it.id }) { record ->
                    HistoryCard(
                        record = record,
                        expanded = expandedId == record.id,
                        onToggle = { expandedId = if (expandedId == record.id) null else record.id },
                        onDelete = { onDelete(record.id) }
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
            confirmButton = { TextButton(onClick = { onClear(); showClear = false }) { Text(stringResource(R.string.action_delete_all_caps)) } },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text(stringResource(R.string.action_cancel_caps)) } }
        )
    }
}

@Composable
private fun HistoryCard(record: WorkoutRecord, expanded: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.profileName, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(formatDate(record.startedAtEpochMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                if (record.aborted) AssistChip(onClick = {}, label = { Text(stringResource(R.string.label_aborted)) })
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.cd_delete_training)) }
            }

            Text(stringResource(R.string.history_mode_target_line, record.mode.displayName(), record.targetLabel))
            Text(
                stringResource(
                    R.string.history_stats_line,
                    record.completedRounds,
                    formatDistance(record.completedDistanceMeters),
                    formatDuration(record.durationMs)
                ),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            TextButton(onClick = onToggle) {
                Text(if (expanded) stringResource(R.string.action_hide_details) else stringResource(R.string.action_show_rounds))
            }

            if (expanded) {
                HorizontalDivider()
                record.rounds.forEach { round ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(stringResource(R.string.round_summary_line, round.number, formatDuration(round.durationMs)), fontWeight = FontWeight.Bold)
                        Text(
                            buildRoundLine(round.distanceMeters, round.routeLoadName, round.routeLoadDetail, round.obstacleName, round.obstacleDetail),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
