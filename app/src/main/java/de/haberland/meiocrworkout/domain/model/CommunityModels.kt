package de.haberland.meiocrworkout.domain.model

enum class CommunityRole {
    USER,
    MODERATOR,
    ADMIN;

    val canModerate: Boolean get() = this == MODERATOR || this == ADMIN
    val canManageModerators: Boolean get() = this == ADMIN
}

data class CommunityUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val role: CommunityRole,
)

data class CommunityProfile(
    val id: String,
    val profile: WorkoutProfile,
    val description: String,
    val location: String,
    val tags: List<String>,
    val creatorName: String,
    val ownerUid: String,
    val updatedAtEpochMs: Long,
)

data class ProfileSubmission(
    val id: String,
    val targetProfileId: String,
    val profile: WorkoutProfile,
    val description: String,
    val location: String,
    val tags: List<String>,
    val creatorName: String,
    val ownerUid: String,
    val ownerEmail: String,
    val submittedAtEpochMs: Long,
    val status: String,
    val moderatorNote: String = "",
)
