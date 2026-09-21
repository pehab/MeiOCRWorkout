package de.haberland.meiocrworkout

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AppViewModel is a plain ViewModel with both of its Android-shaped dependencies
 * (persistence, target-label lookup) injected - see its class doc - so these tests run
 * on the plain JVM with FakeWorkoutRepository and a trivial label lambda. No Robolectric,
 * no mocked Application, no Room.
 *
 * viewModelScope always dispatches on Dispatchers.Main, so every test needs Main pointed
 * at a TestDispatcher (setUp/tearDown below) and runs inside runTest(dispatcher) using
 * that SAME dispatcher instance, so advanceUntilIdle() actually drives the coroutines
 * AppViewModel launches internally (init{}, saveProfiles(), finishWorkout(), etc.).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repo: FakeWorkoutRepository = FakeWorkoutRepository(),
        resolveTargetLabel: (WorkoutPlan) -> String = { "5 Runden" }
    ) = AppViewModel(repo, resolveTargetLabel)

    @Test
    fun loadsDefaultProfileAndMarksInitializedWhenNothingWasSavedYet() = runTest(dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertTrue(vm.initialized)
        assertEquals(1, repo.initializeCallCount)
        assertEquals(listOf(AppRepository.defaultProfile()), vm.profiles)
        assertEquals("standard", vm.selectedProfileId)
    }

    @Test
    fun restoresThePreviouslySelectedProfileWhenItStillExists() = runTest(dispatcher) {
        val other = AppRepository.emptyProfile("Garten")
        val repo = FakeWorkoutRepository(
            initialProfiles = listOf(AppRepository.defaultProfile(), other),
            initialSelectedProfileId = other.id
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(other.id, vm.selectedProfileId)
    }

    @Test
    fun fallsBackToTheFirstProfileWhenTheSavedSelectionNoLongerExists() = runTest(dispatcher) {
        val repo = FakeWorkoutRepository(
            initialProfiles = listOf(AppRepository.defaultProfile()),
            initialSelectedProfileId = "some-deleted-profile-id"
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals("standard", vm.selectedProfileId)
    }

    @Test
    fun saveProfilesReassignsSelectionWhenTheCurrentProfileWasRemoved() = runTest(dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        val replacement = AppRepository.emptyProfile("Daheim")
        vm.saveProfiles(listOf(replacement))
        advanceUntilIdle()

        assertEquals(replacement.id, vm.selectedProfileId)
        assertEquals(listOf(replacement), repo.loadProfiles())
    }

    @Test
    fun saveProfilesFallsBackToADefaultProfileWhenGivenAnEmptyList() = runTest(dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.saveProfiles(emptyList())
        advanceUntilIdle()

        assertEquals(listOf(AppRepository.defaultProfile()), vm.profiles)
    }

    @Test
    fun selectProfilePersistsTheChoice() = runTest(dispatcher) {
        val other = AppRepository.emptyProfile("Garten")
        val repo = FakeWorkoutRepository(initialProfiles = listOf(AppRepository.defaultProfile(), other))
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.selectProfile(other.id)
        advanceUntilIdle()

        assertEquals(other.id, vm.selectedProfileId)
        assertEquals(other.id, repo.loadSelectedProfileId())
    }

    @Test
    fun startWorkoutStoresThePlanAndSwitchesToTheWorkoutScreen() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val plan = SessionGenerator.byRounds(AppRepository.defaultProfile(), 5)
        vm.startWorkout(plan)

        assertEquals(plan, vm.plan)
        assertEquals(AppScreen.WORKOUT, vm.appScreen)
    }

    @Test
    fun finishWorkoutWithCompletedRoundsAddsHistoryAndShowsSummary() = runTest(dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo, resolveTargetLabel = { "5 Runden" })
        advanceUntilIdle()

        val plan = SessionGenerator.byRounds(AppRepository.defaultProfile(), 5, Random(1))
        vm.startWorkout(plan)
        vm.finishWorkout(
            WorkoutResult(
                lapTimes = listOf(1000L, 2000L, 1500L),
                durationMs = 4500L,
                startedAtEpochMs = 0L,
                aborted = false
            )
        )
        advanceUntilIdle()

        assertEquals(AppScreen.SUMMARY, vm.appScreen)
        assertEquals(1, vm.history.size)
        assertEquals(3, vm.history.first().completedRounds)
        assertEquals("5 Runden", vm.history.first().targetLabel)
        assertEquals(vm.history.first(), vm.summaryRecord)
        assertEquals(1, repo.loadHistory().size)
    }

    @Test
    fun finishWorkoutWithNoCompletedRoundsGoesStraightBackToRootWithoutTouchingHistory() = runTest(dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        val plan = SessionGenerator.byRounds(AppRepository.defaultProfile(), 5)
        vm.startWorkout(plan)
        vm.finishWorkout(WorkoutResult(lapTimes = emptyList(), durationMs = 0L, startedAtEpochMs = 0L, aborted = true))
        advanceUntilIdle()

        assertEquals(AppScreen.ROOT, vm.appScreen)
        assertTrue(vm.history.isEmpty())
        assertTrue(repo.loadHistory().isEmpty())
    }

    @Test
    fun cancelWorkoutWithoutRoundsReturnsToRoot() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.startWorkout(SessionGenerator.byRounds(AppRepository.defaultProfile(), 3))

        vm.cancelWorkoutWithoutRounds()

        assertEquals(AppScreen.ROOT, vm.appScreen)
    }

    @Test
    fun backToTrainFromSummarySwitchesScreenAndTab() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.rootTab = RootTab.HISTORY

        vm.backToTrainFromSummary()

        assertEquals(AppScreen.ROOT, vm.appScreen)
        assertEquals(RootTab.TRAIN, vm.rootTab)
    }

    @Test
    fun deleteHistoryRemovesTheEntryLocallyAndFromTheRepository() = runTest(dispatcher) {
        val existing = WorkoutRecord(
            id = "keep-me",
            startedAtEpochMs = 0L,
            profileName = "Standard",
            mode = WorkoutMode.ROUNDS,
            targetLabel = "5 Runden",
            durationMs = 1000L,
            aborted = false,
            rounds = emptyList()
        )
        val toDelete = existing.copy(id = "delete-me")
        val repo = FakeWorkoutRepository(initialHistory = listOf(existing, toDelete))
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.deleteHistory("delete-me")
        advanceUntilIdle()

        assertEquals(listOf(existing), vm.history)
        assertEquals(listOf(existing), repo.loadHistory())
    }

    @Test
    fun clearHistoryEmptiesLocalStateAndTheRepository() = runTest(dispatcher) {
        val record = WorkoutRecord(
            startedAtEpochMs = 0L,
            profileName = "Standard",
            mode = WorkoutMode.ROUNDS,
            targetLabel = "5 Runden",
            durationMs = 1000L,
            aborted = false,
            rounds = emptyList()
        )
        val repo = FakeWorkoutRepository(initialHistory = listOf(record))
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.clearHistory()
        advanceUntilIdle()

        assertTrue(vm.history.isEmpty())
        assertTrue(repo.loadHistory().isEmpty())
    }
}
