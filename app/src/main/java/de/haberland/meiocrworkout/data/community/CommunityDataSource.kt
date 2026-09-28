package de.haberland.meiocrworkout.data.community

import android.app.Activity
import de.haberland.meiocrworkout.domain.model.*

interface CommunityDataSource {
    suspend fun signInWithGoogle(activity: Activity): CommunityUser
    fun signOut()
    suspend fun loadCurrentUser(): CommunityUser?
    suspend fun loadPublishedProfiles(): List<CommunityProfile>
    suspend fun loadOwnSubmissions(): List<ProfileSubmission>
    suspend fun loadPendingSubmissions(): List<ProfileSubmission>
    suspend fun submitProfile(profile: WorkoutProfile, description: String, location: String, tags: List<String>)
    suspend fun approveSubmission(submissionId: String)
    suspend fun rejectSubmission(submissionId: String, note: String)
    suspend fun setModerator(email: String, enabled: Boolean)
    fun importCopy(profile: WorkoutProfile): WorkoutProfile
}
