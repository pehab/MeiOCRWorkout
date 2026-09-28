package de.haberland.meiocrworkout.presentation

import android.app.Activity
import de.haberland.meiocrworkout.data.community.CommunityDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.haberland.meiocrworkout.data.community.CommunityRepository
import de.haberland.meiocrworkout.domain.model.CommunityProfile
import de.haberland.meiocrworkout.domain.model.CommunityUser
import de.haberland.meiocrworkout.domain.model.ProfileSubmission
import de.haberland.meiocrworkout.domain.model.WorkoutProfile
import kotlinx.coroutines.launch

class CommunityViewModel(
    private val repository: CommunityDataSource = CommunityRepository(),
) : ViewModel() {

    var user by mutableStateOf<CommunityUser?>(null)
        private set

    var publishedProfiles by mutableStateOf<List<CommunityProfile>>(emptyList())
        private set

    var ownSubmissions by mutableStateOf<List<ProfileSubmission>>(emptyList())
        private set

    var pendingSubmissions by mutableStateOf<List<ProfileSubmission>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var operation: Job? = null
    private var generation = 0L

    init {
        refreshAll()
    }

    fun refreshAll() {
        runOperation {
            runCatching {
                user = repository.loadCurrentUser()
                publishedProfiles = repository.loadPublishedProfiles()
                ownSubmissions = if (user != null) repository.loadOwnSubmissions() else emptyList()
                pendingSubmissions = if (user?.role?.canModerate == true) {
                    repository.loadPendingSubmissions()
                } else {
                    emptyList()
                }
            }.onFailure(::showError)
        }
    }

    fun signIn(activity: Activity) {
        runOperation {
            runCatching {
                user = repository.signInWithGoogle(activity)
                ownSubmissions = repository.loadOwnSubmissions()
                pendingSubmissions = if (user?.role?.canModerate == true) {
                    repository.loadPendingSubmissions()
                } else emptyList()
            }.onFailure(::showError)
        }
    }

    fun signOut() {
        generation++
        operation?.cancel()
        operation = null
        loading = false
        errorMessage = null
        repository.signOut()
        user = null
        ownSubmissions = emptyList()
        pendingSubmissions = emptyList()
    }

    fun submitProfile(
        profile: WorkoutProfile,
        description: String,
        location: String,
        tags: List<String>,
        onSubmitted: () -> Unit = {},
    ) {
        runOperation {
            runCatching {
                repository.submitProfile(profile, description, location, tags)
                ownSubmissions = repository.loadOwnSubmissions()
            }.onSuccess {
                onSubmitted()
            }.onFailure(::showError)
        }
    }

    fun approve(submissionId: String) {
        runOperation {
            runCatching {
                repository.approveSubmission(submissionId)
                pendingSubmissions = repository.loadPendingSubmissions()
                publishedProfiles = repository.loadPublishedProfiles()
            }.onFailure(::showError)
        }
    }

    fun reject(submissionId: String, note: String) {
        runOperation {
            runCatching {
                repository.rejectSubmission(submissionId, note)
                pendingSubmissions = repository.loadPendingSubmissions()
            }.onFailure(::showError)
        }
    }

    fun setModerator(email: String, enabled: Boolean) {
        runOperation {
            runCatching {
                repository.setModerator(email, enabled)
            }.onFailure(::showError)
        }
    }

    fun importedCopy(profile: WorkoutProfile): WorkoutProfile = repository.importCopy(profile)

    fun clearError() {
        errorMessage = null
    }

    private fun runOperation(block: suspend () -> Unit) {
        // Ignore duplicate taps and refreshes while a request is in flight.
        if (operation?.isActive == true) return
        val startedGeneration = generation
        operation = viewModelScope.launch {
            loading = true
            errorMessage = null
            try {
                block()
            } finally {
                if (generation == startedGeneration) loading = false
            }
        }
    }

    private fun showError(error: Throwable) {
        if (error is CancellationException) throw error
        errorMessage = error.message ?: error::class.java.simpleName
    }
}
