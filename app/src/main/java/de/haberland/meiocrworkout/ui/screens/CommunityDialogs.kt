package de.haberland.meiocrworkout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.haberland.meiocrworkout.R
import de.haberland.meiocrworkout.domain.model.CommunityProfile
import de.haberland.meiocrworkout.domain.model.CommunityUser
import de.haberland.meiocrworkout.domain.model.ProfileSubmission
import de.haberland.meiocrworkout.domain.model.WorkoutProfile
import de.haberland.meiocrworkout.util.formatDate

@Composable
fun CommunityHubDialog(
    profiles: List<CommunityProfile>,
    ownSubmissions: List<ProfileSubmission>,
    pendingSubmissions: List<ProfileSubmission>,
    user: CommunityUser?,
    loading: Boolean,
    onImport: (CommunityProfile) -> Unit,
    onRefresh: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    onSetModerator: (String, Boolean) -> Unit,
    onBootstrapAdmin: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tabs = buildList {
        add(CommunityTab.DISCOVER)
        if (user != null) add(CommunityTab.MY_SUBMISSIONS)
        if (user?.role?.canModerate == true) add(CommunityTab.MODERATION)
        if (user?.role?.canManageModerators == true) add(CommunityTab.ADMIN)
    }
    var selected by remember(tabs, user?.role) { mutableIntStateOf(0) }
    val safeSelected = selected.coerceIn(0, tabs.lastIndex)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.community_title), fontWeight = FontWeight.Black)
                Text(
                    if (user == null) {
                        stringResource(R.string.community_public_hint)
                    } else {
                        stringResource(
                            R.string.community_signed_in_as,
                            user.displayName.ifBlank { user.email },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 650.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (tabs.size > 1) {
                    PrimaryTabRow(selectedTabIndex = safeSelected) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = safeSelected == index,
                                onClick = { selected = index },
                                text = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }

                when (tabs[safeSelected]) {
                    CommunityTab.DISCOVER -> DiscoverProfiles(
                        profiles = profiles,
                        loading = loading,
                        onImport = onImport,
                        onRefresh = onRefresh,
                    )
                    CommunityTab.MY_SUBMISSIONS -> MySubmissions(ownSubmissions)
                    CommunityTab.MODERATION -> ModerationList(
                        submissions = pendingSubmissions,
                        onApprove = onApprove,
                        onReject = onReject,
                    )
                    CommunityTab.ADMIN -> AdminPanel(
                        onSetModerator = onSetModerator,
                        onBootstrapAdmin = onBootstrapAdmin,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

private enum class CommunityTab(val labelRes: Int) {
    DISCOVER(R.string.community_tab_discover),
    MY_SUBMISSIONS(R.string.community_tab_mine),
    MODERATION(R.string.community_tab_moderation),
    ADMIN(R.string.community_tab_admin),
}

@Composable
private fun DiscoverProfiles(
    profiles: List<CommunityProfile>,
    loading: Boolean,
    onImport: (CommunityProfile) -> Unit,
    onRefresh: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val query = search.trim().lowercase()
    val filtered = remember(profiles, query) {
        if (query.isBlank()) {
            profiles
        } else {
            profiles.filter { item ->
                listOf(
                    item.profile.name,
                    item.description,
                    item.location,
                    item.creatorName,
                    item.tags.joinToString(" "),
                ).any { it.lowercase().contains(query) }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(60) },
            label = { Text(stringResource(R.string.community_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.community_profile_count, filtered.size),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRefresh, enabled = !loading) {
                Text(stringResource(R.string.action_refresh))
            }
        }

        if (filtered.isEmpty()) {
            Text(
                if (loading) {
                    stringResource(R.string.community_loading)
                } else {
                    stringResource(R.string.community_empty)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(item.profile.name, fontWeight = FontWeight.Black)
                            if (item.location.isNotBlank()) {
                                Text(
                                    item.location,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (item.description.isNotBlank()) Text(item.description)
                            Text(
                                stringResource(
                                    R.string.community_profile_meta,
                                    item.profile.obstacles.size,
                                    item.profile.distances.size,
                                    item.creatorName.ifBlank {
                                        stringResource(R.string.community_unknown_author)
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { onImport(item) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.action_import_profile))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MySubmissions(submissions: List<ProfileSubmission>) {
    if (submissions.isEmpty()) {
        Text(
            stringResource(R.string.community_no_submissions),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(submissions, key = { it.id }) { submission ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(submission.profile.name, fontWeight = FontWeight.Black)
                    Text(
                        stringResource(
                            R.string.community_submission_status,
                            submission.status.uppercase(),
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        formatDate(submission.submittedAtEpochMs),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (submission.moderatorNote.isNotBlank()) {
                        Text(
                            stringResource(
                                R.string.community_moderator_note,
                                submission.moderatorNote,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModerationList(
    submissions: List<ProfileSubmission>,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
) {
    if (submissions.isEmpty()) {
        Text(
            stringResource(R.string.community_no_pending),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(submissions, key = { it.id }) { submission ->
            var rejectNote by remember(submission.id) { mutableStateOf("") }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(submission.profile.name, fontWeight = FontWeight.Black)
                    Text(
                        stringResource(
                            R.string.community_submitted_by,
                            submission.creatorName.ifBlank { submission.ownerEmail },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (submission.location.isNotBlank()) Text(submission.location)
                    if (submission.description.isNotBlank()) Text(submission.description)
                    Text(
                        stringResource(
                            R.string.community_profile_counts,
                            submission.profile.obstacles.size,
                            submission.profile.routeLoads.size,
                            submission.profile.distances.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onApprove(submission.id) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_approve))
                        }
                        OutlinedButton(
                            onClick = { onReject(submission.id, rejectNote) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_reject))
                        }
                    }

                    OutlinedTextField(
                        value = rejectNote,
                        onValueChange = { rejectNote = it.take(200) },
                        label = { Text(stringResource(R.string.community_reject_note)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminPanel(
    onSetModerator: (String, Boolean) -> Unit,
    onBootstrapAdmin: () -> Unit,
) {
    var email by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.community_admin_hint))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.take(120) },
            label = { Text(stringResource(R.string.community_moderator_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSetModerator(email, true) },
                enabled = email.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_make_moderator))
            }
            OutlinedButton(
                onClick = { onSetModerator(email, false) },
                enabled = email.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_remove_moderator))
            }
        }
        TextButton(onClick = onBootstrapAdmin) {
            Text(stringResource(R.string.action_refresh_admin_role))
        }
    }
}

@Composable
fun PublishProfileDialog(
    profile: WorkoutProfile,
    onSubmit: (description: String, location: String, tags: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.community_publish_title, profile.name),
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.community_publish_hint))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text(stringResource(R.string.community_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.take(100) },
                    label = { Text(stringResource(R.string.community_location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it.take(160) },
                    label = { Text(stringResource(R.string.community_tags)) },
                    supportingText = { Text(stringResource(R.string.community_tags_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        description.trim(),
                        location.trim(),
                        tags.split(',').map(String::trim).filter(String::isNotBlank),
                    )
                },
            ) {
                Text(stringResource(R.string.action_submit_for_review))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel_caps))
            }
        },
    )
}
