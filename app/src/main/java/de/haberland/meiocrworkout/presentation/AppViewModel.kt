package de.haberland.meiocrworkout.presentation

import de.haberland.meiocrworkout.R
import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

enum class RootTab { TRAIN, PROFILES, HISTORY }
enum class AppScreen { ROOT, WORKOUT, SUMMARY }

/**
 * Holds the app's profile/history/navigation state and talks to [WorkoutRepository].
 *
 * This used to live as `remember { mutableStateOf(...) }` variables directly inside
 * the root composable. That meant every field - including which screen was open and
 * the in-progress [plan] - was lost on a plain configuration change (e.g. a screen
 * rotation, since the manifest does not lock orientation): mid-workout, rotating the
 * device silently dropped the user back to the Trainieren tab. Moving this state into
 * a ViewModel survives configuration changes and gets the app/navigation logic out of
 * Compose so it can be unit tested independently of the UI.
 *
 * Deliberately a plain [ViewModel], not [androidx.lifecycle.AndroidViewModel]: both
 * dependencies that would otherwise need a real Context - persistence and the
 * localized target-label lookup - are constructor-injected instead ([repo] and
 * [resolveTargetLabel]). That makes this class 100% Android-free and testable on the
 * plain JVM with a fake repository and a trivial lambda, no Robolectric or mocked
 * Application object required. See app/src/test/.../AppViewModelTest.kt and
 * FakeWorkoutRepository.kt. Production wiring (the real AppRepository and the real
 * Context.getString()-based label lookup) happens once, in MainActivity.kt's
 * viewModelFactory, where a real Context is available.
 */
class AppViewModel(
    private val repo: WorkoutRepository,
    private val resolveTargetLabel: (WorkoutPlan) -> String
) : ViewModel() {

    var profiles by mutableStateOf<List<WorkoutProfile>>(emptyList())
        private set
    var selectedProfileId by mutableStateOf<String?>(null)
        private set
    var history by mutableStateOf<List<WorkoutRecord>>(emptyList())
        private set
    var initialized by mutableStateOf(false)
        private set

    var rootTab by mutableStateOf(RootTab.TRAIN)
    var appScreen by mutableStateOf(AppScreen.ROOT)
        private set
    var plan by mutableStateOf<WorkoutPlan?>(null)
        private set
    var summaryRecord by mutableStateOf<WorkoutRecord?>(null)
        private set

    init {
        viewModelScope.launch {
            repo.initialize()
            val loadedProfiles = repo.loadProfiles().ifEmpty { listOf(AppRepository.defaultProfile()) }
            profiles = loadedProfiles

            val savedProfileId = repo.loadSelectedProfileId()
            val resolvedProfileId = savedProfileId
                ?.takeIf { saved -> loadedProfiles.any { it.id == saved } }
                ?: loadedProfiles.first().id
            selectedProfileId = resolvedProfileId
            if (savedProfileId != resolvedProfileId) repo.saveSelectedProfileId(resolvedProfileId)

            history = repo.loadHistory()
            initialized = true
        }
    }

    fun saveProfiles(updated: List<WorkoutProfile>) {
        val safe = updated.ifEmpty { listOf(AppRepository.defaultProfile()) }
        profiles = safe

        val current = selectedProfileId
        if (current == null || safe.none { it.id == current }) {
            val replacement = safe.first().id
            selectedProfileId = replacement
            viewModelScope.launch { repo.saveSelectedProfileId(replacement) }
        }
        viewModelScope.launch { repo.saveProfiles(safe) }
    }

    fun selectProfile(id: String) {
        selectedProfileId = id
        viewModelScope.launch { repo.saveSelectedProfileId(id) }
    }

    fun startWorkout(generatedPlan: WorkoutPlan) {
        plan = generatedPlan
        appScreen = AppScreen.WORKOUT
    }

    fun cancelWorkoutWithoutRounds() {
        appScreen = AppScreen.ROOT
    }

    fun finishWorkout(result: WorkoutResult) {
        val activePlan = plan ?: return
        val completedRounds = activePlan.rounds.take(result.lapTimes.size)
        val roundRecords = completedRounds.mapIndexed { index, round ->
            WorkoutRoundRecord(
                number = index + 1,
                distanceMeters = round.distance?.meters ?: 0,
                routeLoadName = round.routeLoad?.name,
                routeLoadDetail = round.routeLoad?.detail,
                obstacleName = round.obstacle?.name,
                obstacleDetail = round.obstacle?.detail,
                durationMs = result.lapTimes.getOrElse(index) { 0L }
            )
        }
        val record = WorkoutRecord(
            startedAtEpochMs = result.startedAtEpochMs,
            profileName = activePlan.profileName,
            mode = activePlan.mode,
            targetLabel = resolveTargetLabel(activePlan),
            durationMs = result.durationMs,
            aborted = result.aborted,
            rounds = roundRecords
        )
        if (roundRecords.isNotEmpty()) {
            history = (listOf(record) + history).distinctBy { it.id }.take(Limits.MAX_HISTORY_RECORDS)
            viewModelScope.launch { history = repo.addHistory(record) }
            summaryRecord = record
            appScreen = AppScreen.SUMMARY
        } else {
            appScreen = AppScreen.ROOT
        }
    }

    fun backToTrainFromSummary() {
        appScreen = AppScreen.ROOT
        rootTab = RootTab.TRAIN
    }

    fun deleteHistory(id: String) {
        history = history.filterNot { it.id == id }
        viewModelScope.launch { history = repo.deleteHistory(id) }
    }

    fun clearHistory() {
        history = emptyList()
        viewModelScope.launch { repo.clearHistory() }
    }
}

/**
 * The real, production [WorkoutPlan] -> target-label resolver: builds the human-readable
 * label ("8 Runden", "10 km", "60 Minuten", "offen") that gets stored on [WorkoutRecord]
 * and never recomputed afterwards.
 *
 * This can't be a @Composable using stringResource() like WorkoutMode.displayName() or
 * buildRoundLine() in UiStrings.kt: its result is written straight into the database via
 * WorkoutEntity.targetLabel, so it needs to run once, synchronously, at save time - not
 * be recomputed on every recomposition. It resolves the string with the plain
 * Context.getString() API instead. AppViewModel never calls this directly; MainActivity.kt
 * wires it in as [AppViewModel]'s resolveTargetLabel constructor argument, which is what
 * keeps the ViewModel itself free of any Context dependency.
 *
 * That also means a recorded workout's target label is fixed in whatever the device's
 * locale was at the moment it was saved - switching the app to English later will not
 * retroactively translate old history entries. That is intentional: it is the same
 * "history keeps its original wording" behaviour you'd expect from a log or diary entry,
 * not a bug to fix. If that's ever undesirable, the real fix is to stop persisting a
 * pre-formatted label at all and instead store the raw WorkoutMode + target number,
 * formatting the label at display time in the UI layer (the same way buildRoundLine()
 * already does for round details) - a bigger, deliberate change to the History schema,
 * not something to fold in here.
 */
internal fun defaultTargetLabel(context: Context, plan: WorkoutPlan): String = when (plan.mode) {
    WorkoutMode.ROUNDS -> context.getString(R.string.target_rounds, plan.requestedRounds ?: plan.rounds.size)
    WorkoutMode.DISTANCE -> formatDistance(plan.requestedDistanceMeters ?: plan.plannedDistanceMeters)
    WorkoutMode.AMRAP_TIME -> context.getString(R.string.target_amrap_minutes, plan.requestedDurationMinutes ?: 0)
    WorkoutMode.AMRAP_OPEN -> context.getString(R.string.target_open)
}
