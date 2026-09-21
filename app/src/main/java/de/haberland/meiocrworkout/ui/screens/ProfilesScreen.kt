package de.haberland.meiocrworkout.ui.screens

import de.haberland.meiocrworkout.R
import de.haberland.meiocrworkout.BuildConfig
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfilesScreen(
    profiles: List<WorkoutProfile>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit,
    onProfilesChanged: (List<WorkoutProfile>) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var showNewDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }

    val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.first()

    fun updateProfile(updated: WorkoutProfile) {
        onProfilesChanged(profiles.map { if (it.id == updated.id) updated else it })
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.title_profiles), fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                OutlinedButton(onClick = { profileMenu = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(profile.name, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                    profiles.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = { onProfileSelected(p.id); profileMenu = false }
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { showNewDialog = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_new)) }
                OutlinedButton(onClick = {
                    val copy = AppRepository.duplicateProfile(profile)
                    onProfilesChanged(profiles + copy)
                    onProfileSelected(copy.id)
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_duplicate)) }
                OutlinedButton(onClick = { showRenameDialog = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_rename)) }
                IconButton(onClick = { showDeleteDialog = true }, enabled = profiles.size > 1) {
                    Icon(Icons.Default.Delete, stringResource(R.string.cd_delete_profile))
                }
            }
        }

        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.tab_obstacles)) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.tab_route_load)) })
            Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.tab_distance)) })
        }

        when (tab) {
            0 -> WeightedEditor(
                items = profile.obstacles,
                onChange = { updateProfile(profile.copy(obstacles = it)) },
                addLabel = stringResource(R.string.add_label_obstacle),
                allowNone = false
            )
            1 -> WeightedEditor(
                items = profile.routeLoads,
                onChange = { updateProfile(profile.copy(routeLoads = it)) },
                addLabel = stringResource(R.string.add_label_route_load),
                allowNone = true
            )
            2 -> DistanceEditor(
                items = profile.distances,
                onChange = { updateProfile(profile.copy(distances = it)) }
            )
        }
    }

    if (showNewDialog) {
        NameDialog(
            title = stringResource(R.string.dialog_new_profile_title),
            label = stringResource(R.string.label_profile_name),
            initial = stringResource(R.string.default_new_profile_name),
            onConfirm = { name ->
                val newProfile = AppRepository.emptyProfile(name)
                onProfilesChanged(profiles + newProfile)
                onProfileSelected(newProfile.id)
                showNewDialog = false
            },
            onDismiss = { showNewDialog = false }
        )
    }

    if (showRenameDialog) {
        NameDialog(
            title = stringResource(R.string.dialog_rename_profile_title),
            label = stringResource(R.string.label_profile_name),
            initial = profile.name,
            onConfirm = { name ->
                updateProfile(profile.copy(name = name))
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_profile_title)) },
            text = { Text(stringResource(R.string.dialog_delete_profile_text, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val remaining = profiles.filterNot { it.id == profile.id }
                    onProfilesChanged(remaining)
                    onProfileSelected(remaining.first().id)
                    showDeleteDialog = false
                }) { Text(stringResource(R.string.action_delete_caps)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel_caps)) } }
        )
    }
}

@Composable
private fun NameDialog(title: String, label: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it.take(40) }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel_caps)) } }
    )
}

@Composable
private fun WeightedEditor(
    items: List<WeightedItem>,
    onChange: (List<WeightedItem>) -> Unit,
    addLabel: String,
    allowNone: Boolean
) {
    var newName by remember { mutableStateOf("") }
    var newDetail by remember { mutableStateOf("") }
    var editItem by remember { mutableStateOf<WeightedItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        newName,
                        { newName = it.take(50) },
                        label = { Text(addLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        newDetail,
                        { newDetail = it.take(60) },
                        label = { Text(stringResource(R.string.label_detail_optional_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onChange(items + WeightedItem(name = newName.trim(), detail = newDetail.trim()))
                                newName = ""
                                newDetail = ""
                            }
                        },
                        enabled = newName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.action_add_caps)) }

                    if (allowNone && items.none { it.isNone }) {
                        OutlinedButton(
                            // "Keine" here is the internal marker value stored on the free-turnaround
                            // WeightedItem, not display text - the actual label shown for it.isNone
                            // items is label_none_free below, resolved fresh at render time.
                            onClick = { onChange(listOf(WeightedItem(name = "Keine", weight = 3, isNone = true)) + items) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.action_add_free_turn)) }
                    }
                }
            }
        }

        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            ItemEditorRow(
                item = item,
                onEnabled = { enabled -> onChange(items.toMutableList().also { it[index] = item.copy(enabled = enabled) }) },
                onWeight = { weight -> onChange(items.toMutableList().also { it[index] = item.copy(weight = weight) }) },
                onEdit = { if (!item.isNone) editItem = item },
                onDelete = { onChange(items.filterIndexed { i, _ -> i != index }) }
            )
        }
    }

    editItem?.let { current ->
        var name by remember(current.id) { mutableStateOf(current.name) }
        var detail by remember(current.id) { mutableStateOf(current.detail) }
        AlertDialog(
            onDismissRequest = { editItem = null },
            title = { Text(stringResource(R.string.dialog_edit_item_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(50) }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true)
                    OutlinedTextField(detail, { detail = it.take(60) }, label = { Text(stringResource(R.string.label_detail_optional)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val idx = items.indexOfFirst { it.id == current.id }
                    if (idx >= 0 && name.isNotBlank()) {
                        onChange(items.toMutableList().also { it[idx] = current.copy(name = name.trim(), detail = detail.trim()) })
                    }
                    editItem = null
                }) { Text(stringResource(R.string.action_save_caps)) }
            },
            dismissButton = { TextButton(onClick = { editItem = null }) { Text(stringResource(R.string.action_cancel_caps)) } }
        )
    }
}

@Composable
private fun ItemEditorRow(
    item: WeightedItem,
    onEnabled: (Boolean) -> Unit,
    onWeight: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = item.enabled, onCheckedChange = onEnabled)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (item.isNone) stringResource(R.string.label_none_free) else item.name, fontWeight = FontWeight.Bold)
                if (item.detail.isNotBlank()) Text(item.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.item_weight, item.weight), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = { onWeight((item.weight - 1).coerceAtLeast(Limits.WEIGHT_MIN)) }) { Text("−") }
            TextButton(onClick = { onWeight((item.weight + 1).coerceAtMost(Limits.WEIGHT_MAX)) }) { Text("+") }
            if (!item.isNone) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.cd_edit)) }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.cd_delete)) }
        }
    }
}

@Composable
private fun DistanceEditor(items: List<DistanceOption>, onChange: (List<DistanceOption>) -> Unit) {
    var newMeters by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.distance_editor_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            newMeters,
                            { newMeters = it.filter(Char::isDigit).take(5) },
                            label = { Text(stringResource(R.string.label_new_simple_distance)) },
                            suffix = { Text(stringResource(R.string.suffix_m)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        IconButton(onClick = {
                            val m = newMeters.toIntOrNull()
                            if (m != null && m >= 0) {
                                onChange(items + DistanceOption(meters = m))
                                newMeters = ""
                            }
                        }) { Icon(Icons.Default.Add, stringResource(R.string.cd_add)) }
                    }
                }
            }
        }

        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(item.enabled, { enabled -> onChange(items.toMutableList().also { it[index] = item.copy(enabled = enabled) }) })
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label(), fontWeight = FontWeight.Bold)
                        Text(
                            if (item.totalMeters == 0) {
                                stringResource(R.string.distance_meta_direct, item.weight)
                            } else {
                                stringResource(R.string.distance_meta_loop, item.totalMeters, item.weight)
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onChange(items.toMutableList().also { it[index] = item.copy(weight = (item.weight - 1).coerceAtLeast(Limits.WEIGHT_MIN)) }) }) { Text("−") }
                    TextButton(onClick = { onChange(items.toMutableList().also { it[index] = item.copy(weight = (item.weight + 1).coerceAtMost(Limits.WEIGHT_MAX)) }) }) { Text("+") }
                    IconButton(onClick = { onChange(items.filterIndexed { i, _ -> i != index }) }) { Icon(Icons.Default.Delete, stringResource(R.string.cd_delete)) }
                }
            }
        }
    }
}
