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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SummaryScreen(record: WorkoutRecord, onDone: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                if (record.aborted) stringResource(R.string.summary_title_aborted) else stringResource(R.string.summary_title_completed),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                stringResource(R.string.summary_profile_mode_line, record.profileName, record.mode.displayName()),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryValue(stringResource(R.string.label_duration), formatDuration(record.durationMs))
                    SummaryValue(stringResource(R.string.mode_rounds), record.completedRounds.toString())
                    SummaryValue(stringResource(R.string.label_distance_traveled), formatDistance(record.completedDistanceMeters))
                    SummaryValue(stringResource(R.string.label_target), record.targetLabel)
                }
            }
        }
        item { Text(stringResource(R.string.mode_rounds), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        items(record.rounds) { round ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.round_summary_line, round.number, formatDuration(round.durationMs)), fontWeight = FontWeight.Bold)
                    Text(
                        buildRoundLine(round.distanceMeters, round.routeLoadName, round.routeLoadDetail, round.obstacleName, round.obstacleDetail),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.action_done), fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
