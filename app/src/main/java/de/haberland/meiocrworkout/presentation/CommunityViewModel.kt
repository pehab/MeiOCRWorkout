package de.haberland.meiocrworkout.presentation

import android.app.Activity
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
    private val repository: CommunityRepository = CommunityRepository(),
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

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            loading = true
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
            loading = false
        }
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            loading = true
            runCatching {
                user = repository.signInWithGoogle(activity)
                ownSubmissions = repository.loadOwnSubmissions()
                if (user?.role?.canModerate == true) {
                    pendingSubmissions = repository.loadPendingSubmissions()
                }
            }.onFailure(::showError)
            loading = false
        }
    }

    fun signOut() {
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
        viewModelScope.launch {
            loading = true
            runCatching {
                repository.submitProfile(profile, description, location, tags)
                ownSubmissions = repository.loadOwnSubmissions()
            }.onSuccess {
                onSubmitted()
            }.onFailure(::showError)
            loading = false
        }
    }

    fun approve(submissionId: String) {
        viewModelScope.launch {
            loading = true
            runCatching {
                repository.approveSubmission(submissionId)
                pendingSubmissions = repository.loadPendingSubmissions()
                publishedProfiles = repository.loadPublishedProfiles()
            }.onFailure(::showError)
            loading = false
        }
    }

    fun reject(submissionId: String, note: String) {
        viewModelScope.launch {
            loading = true
            runCatching {
                repository.rejectSubmission(submissionId, note)
                pendingSubmissions = repository.loadPendingSubmissions()
            }.onFailure(::showError)
            loading = false
        }
    }

    fun setModerator(email: String, enabled: Boolean) {
        viewModelScope.launch {
            loading = true
            runCatching {
                repository.setModerator(email, enabled)
            }.onFailure(::showError)
            loading = false
        }
    }

    fun importedCopy(profile: WorkoutProfile): WorkoutProfile = repository.importCopy(profile)

    fun clearError() {
        errorMessage = null
    }

    private fun showError(error: Throwable) {
        errorMessage = error.message ?: error::class.java.simpleName
    }
}
