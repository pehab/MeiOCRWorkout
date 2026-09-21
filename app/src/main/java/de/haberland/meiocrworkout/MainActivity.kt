package de.haberland.meiocrworkout

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Hosts the Compose UI and the Play in-app-update check. App/navigation state itself
 * lives in [AppViewModel]; see its class doc for why that split matters (state used
 * to be lost on device rotation before the ViewModel was introduced).
 */
class MainActivity : ComponentActivity() {
    private lateinit var appUpdateManager: AppUpdateManager
    private var updateReadyToInstall by mutableStateOf(false)

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // RESULT_CANCELED is intentionally harmless: the next app start checks again.
        if (result.resultCode == Activity.RESULT_OK) {
            // Flexible updates continue downloading in the background.
        }
    }

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            updateReadyToInstall = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(installStateUpdatedListener)
        checkForFlexibleUpdate()

        setContent {
            MeiOCRWorkoutApp(
                updateReadyToInstall = updateReadyToInstall,
                onInstallUpdate = { appUpdateManager.completeUpdate() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    updateReadyToInstall = true
                }
            }
        }
    }

    override fun onDestroy() {
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        }
        super.onDestroy()
    }

    private fun checkForFlexibleUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> {
                    updateReadyToInstall = true
                }

                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    )
                }
            }
        }
        // A sideloaded alpha build is not owned by Google Play; in that case the
        // task may fail and the app simply continues without an update prompt.
    }
}

/**
 * Builds the real, production [AppViewModel]: a real [AppRepository] talking to Room,
 * and [defaultTargetLabel] resolving strings through the real app Context. AppViewModel
 * itself never sees a Context - only this wiring point does - which is what lets tests
 * construct AppViewModel directly with a fake repository and a trivial label lambda
 * instead. viewModelFactory/initializer come from androidx.lifecycle.viewmodel, not
 * Compose - they replace the reflection-based AndroidViewModelFactory that a plain
 * `viewModel()` call would otherwise use.
 */
@Composable
private fun appViewModel(): AppViewModel {
    val context = LocalContext.current
    val factory = remember {
        viewModelFactory {
            initializer {
                AppViewModel(
                    repo = AppRepository(context.applicationContext),
                    resolveTargetLabel = { plan -> defaultTargetLabel(context.applicationContext, plan) }
                )
            }
        }
    }
    return viewModel(factory = factory)
}

@Composable
fun MeiOCRWorkoutApp(
    updateReadyToInstall: Boolean = false,
    onInstallUpdate: () -> Unit = {},
    viewModel: AppViewModel = appViewModel()
) {
    var updatePromptDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(updateReadyToInstall) {
        if (!updateReadyToInstall) updatePromptDismissed = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB9F36B),
            surface = Color(0xFF171A1D),
            background = Color(0xFF0E1012),
            surfaceVariant = Color(0xFF22272B)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!viewModel.initialized || viewModel.profiles.isEmpty() || viewModel.selectedProfileId == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Surface
            }

            when (viewModel.appScreen) {
                AppScreen.ROOT -> RootShell(
                    tab = viewModel.rootTab,
                    onTab = { viewModel.rootTab = it },
                    content = {
                        val selectedProfile = viewModel.profiles.firstOrNull { it.id == viewModel.selectedProfileId }
                            ?: viewModel.profiles.first()
                        when (viewModel.rootTab) {
                            RootTab.TRAIN -> TrainScreen(
                                profiles = viewModel.profiles,
                                selectedProfileId = selectedProfile.id,
                                onProfileSelected = viewModel::selectProfile,
                                onStart = viewModel::startWorkout
                            )

                            RootTab.PROFILES -> ProfilesScreen(
                                profiles = viewModel.profiles,
                                selectedProfileId = selectedProfile.id,
                                onProfileSelected = viewModel::selectProfile,
                                onProfilesChanged = viewModel::saveProfiles
                            )

                            RootTab.HISTORY -> HistoryScreen(
                                history = viewModel.history,
                                onDelete = viewModel::deleteHistory,
                                onClear = viewModel::clearHistory
                            )
                        }
                    }
                )

                AppScreen.WORKOUT -> viewModel.plan?.let { activePlan ->
                    WorkoutScreen(
                        plan = activePlan,
                        onFinish = viewModel::finishWorkout,
                        onCancelWithoutRounds = viewModel::cancelWorkoutWithoutRounds
                    )
                }

                AppScreen.SUMMARY -> viewModel.summaryRecord?.let { record ->
                    SummaryScreen(record, onDone = viewModel::backToTrainFromSummary)
                }
            }
        }

        if (updateReadyToInstall && viewModel.appScreen != AppScreen.WORKOUT && !updatePromptDismissed) {
            AlertDialog(
                onDismissRequest = { updatePromptDismissed = true },
                title = { Text(stringResource(R.string.update_dialog_title)) },
                text = { Text(stringResource(R.string.update_dialog_text)) },
                confirmButton = {
                    TextButton(onClick = onInstallUpdate) { Text(stringResource(R.string.action_install_caps)) }
                },
                dismissButton = {
                    TextButton(onClick = { updatePromptDismissed = true }) { Text(stringResource(R.string.action_later_caps)) }
                }
            )
        }
    }
}

@Composable
private fun RootShell(tab: RootTab, onTab: (RootTab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == RootTab.TRAIN,
                    onClick = { onTab(RootTab.TRAIN) },
                    icon = { Icon(Icons.Default.PlayArrow, null) },
                    label = { Text(stringResource(R.string.nav_train)) }
                )
                NavigationBarItem(
                    selected = tab == RootTab.PROFILES,
                    onClick = { onTab(RootTab.PROFILES) },
                    icon = { Icon(Icons.Default.Tune, null) },
                    label = { Text(stringResource(R.string.nav_profiles)) }
                )
                NavigationBarItem(
                    selected = tab == RootTab.HISTORY,
                    onClick = { onTab(RootTab.HISTORY) },
                    icon = { Icon(Icons.Default.History, null) },
                    label = { Text(stringResource(R.string.nav_history)) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) { content() }
    }
}
