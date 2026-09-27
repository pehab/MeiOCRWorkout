package de.haberland.meiocrworkout.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.haberland.meiocrworkout.R
import de.haberland.meiocrworkout.domain.model.GroupParticipantConfig

internal val GROUP_COLORS = listOf(
    Color(0xFF8BC34A),
    Color(0xFF42A5F5),
    Color(0xFFFF7043),
    Color(0xFFAB47BC),
    Color(0xFFFFCA28),
    Color(0xFF26C6DA),
    Color(0xFFEC407A),
    Color(0xFF7E57C2),
)

@Composable
fun GroupSetupDialog(
    participantCount: Int,
    onDismiss: () -> Unit,
    onStart: (List<GroupParticipantConfig>) -> Unit,
) {
    val participants = remember(participantCount) {
        mutableStateListOf<GroupParticipantConfig>().apply {
            repeat(participantCount) { index ->
                add(GroupParticipantConfig(colorIndex = index % GROUP_COLORS.size))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.group_setup_title),
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onStart(participants.toList()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.group_start_now))
                }

                Text(
                    text = stringResource(R.string.group_setup_optional_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(participants) { index, participant ->
                        ParticipantEditor(
                            number = index + 1,
                            participant = participant,
                            onChange = { updated -> participants[index] = updated },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel_caps))
            }
        },
    )
}

@Composable
private fun ParticipantEditor(
    number: Int,
    participant: GroupParticipantConfig,
    onChange: (GroupParticipantConfig) -> Unit,
) {
    var name by remember(participant.name) { mutableStateOf(participant.name) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(24)
                onChange(participant.copy(name = name))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.group_participant_name, number)) },
            placeholder = { Text(stringResource(R.string.group_participant_default, number)) },
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.group_color),
                style = MaterialTheme.typography.labelMedium,
            )

            GROUP_COLORS.forEachIndexed { colorIndex, color ->
                val selected = participant.colorIndex == colorIndex
                Box(
                    modifier = Modifier
                        .size(if (selected) 30.dp else 26.dp)
                        .background(
                            color = color,
                            shape = CircleShape,
                        )
                        .clickable {
                            onChange(participant.copy(colorIndex = colorIndex))
                        }
                        .padding(if (selected) 3.dp else 0.dp),
                )
            }
        }
    }
}
