package de.haberland.meiocrworkout

import android.app.Activity
import de.haberland.meiocrworkout.data.community.CommunityDataSource
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.presentation.CommunityViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun signOutCancelsDelayedRefreshWithoutRestoringUser() = runTest(dispatcher) {
        val repository = FakeCommunity()
        repository.gate = CompletableDeferred()
        val vm = CommunityViewModel(repository)
        runCurrent()
        assertTrue(vm.loading)
        vm.signOut()
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertNull(vm.user)
        assertTrue(vm.ownSubmissions.isEmpty())
        assertTrue(vm.pendingSubmissions.isEmpty())
        assertNull(vm.errorMessage)
        assertFalse(vm.loading)
    }

    @Test fun duplicateRefreshIsIgnoredAndFailureCanBeRetried() = runTest(dispatcher) {
        val repository = FakeCommunity()
        repository.failure = IllegalStateException("offline")
        val vm = CommunityViewModel(repository)
        vm.refreshAll()
        advanceUntilIdle()
        assertEquals(1, repository.loads)
        assertEquals("offline", vm.errorMessage)
        assertFalse(vm.loading)
        repository.failure = null
        vm.refreshAll()
        advanceUntilIdle()
        assertEquals(2, repository.loads)
        assertNull(vm.errorMessage)
        assertNotNull(vm.user)
    }

    @Test fun oldCancellationDoesNotClearNewLoadingState() = runTest(dispatcher) {
        val repository = FakeCommunity()
        repository.gate = CompletableDeferred()
        val vm = CommunityViewModel(repository)
        runCurrent()
        vm.signOut()
        vm.refreshAll()
        runCurrent()
        assertTrue(vm.loading)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.loading)
        assertNull(vm.errorMessage)
    }

    @Test fun cancellationIsNotDisplayedAsFailure() = runTest(dispatcher) {
        val repository = FakeCommunity()
        repository.failure = CancellationException("cancelled")
        val vm = CommunityViewModel(repository)
        advanceUntilIdle()
        assertNull(vm.errorMessage)
        assertFalse(vm.loading)
    }

    private class FakeCommunity : CommunityDataSource {
        var gate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        var loads = 0
        var signedOut = false
        override suspend fun loadCurrentUser(): CommunityUser? {
            loads++
            gate?.await()
            failure?.let { throw it }
            return if (signedOut) null else CommunityUser("user", "User", "user@example.test", CommunityRole.USER)
        }
        override fun signOut() { signedOut = true }
        override suspend fun signInWithGoogle(activity: Activity): CommunityUser = error("Unused")
        override suspend fun loadPublishedProfiles() = emptyList<CommunityProfile>()
        override suspend fun loadOwnSubmissions() = emptyList<ProfileSubmission>()
        override suspend fun loadPendingSubmissions() = emptyList<ProfileSubmission>()
        override suspend fun submitProfile(profile: WorkoutProfile, description: String, location: String, tags: List<String>) = Unit
        override suspend fun approveSubmission(submissionId: String) = Unit
        override suspend fun rejectSubmission(submissionId: String, note: String) = Unit
        override suspend fun setModerator(email: String, enabled: Boolean) = Unit
        override fun importCopy(profile: WorkoutProfile) = profile
    }
}
