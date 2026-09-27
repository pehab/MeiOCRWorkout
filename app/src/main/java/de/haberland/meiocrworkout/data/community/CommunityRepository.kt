package de.haberland.meiocrworkout.data.community

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import de.haberland.meiocrworkout.R
import de.haberland.meiocrworkout.domain.model.CommunityProfile
import de.haberland.meiocrworkout.domain.model.CommunityRole
import de.haberland.meiocrworkout.domain.model.CommunityUser
import de.haberland.meiocrworkout.domain.model.DistanceOption
import de.haberland.meiocrworkout.domain.model.ProfileSubmission
import de.haberland.meiocrworkout.domain.model.WeightedItem
import de.haberland.meiocrworkout.domain.model.WorkoutProfile
import java.util.UUID
import kotlinx.coroutines.tasks.await

class CommunityRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun signInWithGoogle(activity: Activity): CommunityUser {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(activity.getString(R.string.default_web_client_id))
                    .build()
            )
            .build()

        val result = CredentialManager.create(activity).getCredential(activity, request)
        val credential = result.credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Google-Anmeldung konnte nicht gelesen werden." }

        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
        auth.signInWithCredential(firebaseCredential).await()
        upsertCommunityUser()
        return requireNotNull(loadCurrentUser())
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun loadCurrentUser(): CommunityUser? {
        val user = auth.currentUser ?: return null
        upsertCommunityUser()

        val roleValue = firestore.collection(ROLES)
            .document(user.uid)
            .get()
            .await()
            .getString("role")
            ?.lowercase()

        val role = when (roleValue) {
            "admin" -> CommunityRole.ADMIN
            "moderator" -> CommunityRole.MODERATOR
            else -> CommunityRole.USER
        }

        return CommunityUser(
            uid = user.uid,
            displayName = user.displayName.orEmpty(),
            email = user.email.orEmpty(),
            role = role,
        )
    }

    suspend fun loadPublishedProfiles(): List<CommunityProfile> =
        firestore.collection(PUBLISHED)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(500)
            .get()
            .await()
            .documents
            .mapNotNull(::toCommunityProfile)

    suspend fun loadOwnSubmissions(): List<ProfileSubmission> {
        val user = auth.currentUser ?: return emptyList()
        return firestore.collection(SUBMISSIONS)
            .whereEqualTo("ownerUid", user.uid)
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()
            .documents
            .mapNotNull(::toSubmission)
    }

    suspend fun loadPendingSubmissions(): List<ProfileSubmission> =
        firestore.collection(SUBMISSIONS)
            .whereEqualTo("status", "pending")
            .orderBy("submittedAt", Query.Direction.ASCENDING)
            .limit(100)
            .get()
            .await()
            .documents
            .mapNotNull(::toSubmission)

    suspend fun submitProfile(
        profile: WorkoutProfile,
        description: String,
        location: String,
        tags: List<String>,
    ) {
        val user = auth.currentUser ?: error("Für das Veröffentlichen ist eine Anmeldung nötig.")
        val targetProfileId = "${user.uid}_${profile.id}"
        val data = mapOf<String, Any?>(
            "targetProfileId" to targetProfileId,
            "ownerUid" to user.uid,
            "ownerEmail" to user.email.orEmpty(),
            "creatorName" to user.displayName.orEmpty(),
            "status" to "pending",
            "submittedAt" to Timestamp.now(),
            "description" to description.trim(),
            "location" to location.trim(),
            "tags" to tags.map(String::trim).filter(String::isNotBlank).distinct().take(12),
            "formatVersion" to 1,
            "profile" to profile.toFirestoreMap(),
        )
        firestore.collection(SUBMISSIONS).add(data).await()
    }

    suspend fun approveSubmission(submissionId: String) {
        val moderator = requireModerator()
        val submissionRef = firestore.collection(SUBMISSIONS).document(submissionId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(submissionRef)
            check(snapshot.exists()) { "Einreichung nicht gefunden." }
            check(snapshot.getString("status") == "pending") { "Die Einreichung ist nicht mehr offen." }

            val targetProfileId = snapshot.getString("targetProfileId").orEmpty()
            check(targetProfileId.isNotBlank()) { "Zielprofil fehlt." }

            val publishedRef = firestore.collection(PUBLISHED).document(targetProfileId)
            val published = mapOf<String, Any?>(
                "ownerUid" to snapshot.getString("ownerUid").orEmpty(),
                "creatorName" to snapshot.getString("creatorName").orEmpty(),
                "description" to snapshot.getString("description").orEmpty(),
                "location" to snapshot.getString("location").orEmpty(),
                "tags" to snapshot.get("tags").asStringList(),
                "formatVersion" to ((snapshot.getLong("formatVersion") ?: 1L).toInt()),
                "profile" to snapshot.get("profile"),
                "updatedAt" to Timestamp.now(),
                "approvedBy" to moderator.uid,
                "sourceSubmissionId" to submissionId,
            )

            transaction.set(publishedRef, published)
            transaction.update(
                submissionRef,
                mapOf(
                    "status" to "approved",
                    "reviewedAt" to Timestamp.now(),
                    "reviewedBy" to moderator.uid,
                    "moderatorNote" to "",
                )
            )
        }.await()
    }

    suspend fun rejectSubmission(submissionId: String, note: String) {
        val moderator = requireModerator()
        val ref = firestore.collection(SUBMISSIONS).document(submissionId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            check(snapshot.exists()) { "Einreichung nicht gefunden." }
            check(snapshot.getString("status") == "pending") { "Die Einreichung ist nicht mehr offen." }

            transaction.update(
                ref,
                mapOf(
                    "status" to "rejected",
                    "reviewedAt" to Timestamp.now(),
                    "reviewedBy" to moderator.uid,
                    "moderatorNote" to note.trim().take(500),
                )
            )
        }.await()
    }

    suspend fun setModerator(email: String, enabled: Boolean) {
        val admin = requireNotNull(loadCurrentUser())
        check(admin.role == CommunityRole.ADMIN) { "Adminrechte erforderlich." }

        val normalizedEmail = email.trim().lowercase()
        check(normalizedEmail.isNotBlank()) { "E-Mail fehlt." }

        val matches = firestore.collection(USERS)
            .whereEqualTo("emailNormalized", normalizedEmail)
            .limit(1)
            .get()
            .await()
            .documents

        val target = matches.firstOrNull()
            ?: error("Kein MeiOCRWorkout-Konto mit dieser E-Mail gefunden. Der Nutzer muss sich mindestens einmal in MeiOCRWorkout anmelden.")

        val targetUid = target.id
        check(targetUid != admin.uid) { "Das eigene Admin-Konto kann hier nicht geändert werden." }

        val roleRef = firestore.collection(ROLES).document(targetUid)
        if (enabled) {
            roleRef.set(
                mapOf(
                    "role" to "moderator",
                    "email" to target.getString("email").orEmpty(),
                    "updatedAt" to Timestamp.now(),
                )
            ).await()
        } else {
            roleRef.delete().await()
        }
    }

    fun importCopy(source: WorkoutProfile): WorkoutProfile = source.copy(
        id = UUID.randomUUID().toString(),
        distances = source.distances.map { it.copy(id = UUID.randomUUID().toString()) },
        routeLoads = source.routeLoads.map { it.copy(id = UUID.randomUUID().toString()) },
        obstacles = source.obstacles.map { it.copy(id = UUID.randomUUID().toString()) },
    )

    private suspend fun upsertCommunityUser() {
        val user = auth.currentUser ?: return
        val email = user.email.orEmpty()
        firestore.collection(USERS)
            .document(user.uid)
            .set(
                mapOf(
                    "email" to email,
                    "emailNormalized" to email.lowercase(),
                    "displayName" to user.displayName.orEmpty(),
                    "updatedAt" to Timestamp.now(),
                )
            )
            .await()
    }

    private suspend fun requireModerator(): CommunityUser {
        val user = requireNotNull(loadCurrentUser()) { "Anmeldung erforderlich." }
        check(user.role.canModerate) { "Moderatorrechte erforderlich." }
        return user
    }

    private fun WorkoutProfile.toFirestoreMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "distances" to distances.map {
            mapOf(
                "meters" to it.meters,
                "weight" to it.weight,
                "enabled" to it.enabled,
            )
        },
        "routeLoads" to routeLoads.map { it.toFirestoreMap() },
        "obstacles" to obstacles.map { it.toFirestoreMap() },
    )

    private fun WeightedItem.toFirestoreMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "weight" to weight,
        "enabled" to enabled,
        "detail" to detail,
        "isNone" to isNone,
    )

    private fun toCommunityProfile(doc: DocumentSnapshot): CommunityProfile? {
        val profile = doc.get("profile").asStringMap()?.toWorkoutProfile() ?: return null
        return CommunityProfile(
            id = doc.id,
            profile = profile,
            description = doc.getString("description").orEmpty(),
            location = doc.getString("location").orEmpty(),
            tags = doc.get("tags").asStringList(),
            creatorName = doc.getString("creatorName").orEmpty(),
            ownerUid = doc.getString("ownerUid").orEmpty(),
            updatedAtEpochMs = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
        )
    }

    private fun toSubmission(doc: DocumentSnapshot): ProfileSubmission? {
        val profile = doc.get("profile").asStringMap()?.toWorkoutProfile() ?: return null
        return ProfileSubmission(
            id = doc.id,
            targetProfileId = doc.getString("targetProfileId").orEmpty(),
            profile = profile,
            description = doc.getString("description").orEmpty(),
            location = doc.getString("location").orEmpty(),
            tags = doc.get("tags").asStringList(),
            creatorName = doc.getString("creatorName").orEmpty(),
            ownerUid = doc.getString("ownerUid").orEmpty(),
            ownerEmail = doc.getString("ownerEmail").orEmpty(),
            submittedAtEpochMs = doc.getTimestamp("submittedAt")?.toDate()?.time ?: 0L,
            status = doc.getString("status").orEmpty(),
            moderatorNote = doc.getString("moderatorNote").orEmpty(),
        )
    }

    private fun Map<String, Any?>.toWorkoutProfile(): WorkoutProfile {
        val distances = this["distances"].asMapList().map { item ->
            DistanceOption(
                meters = (item["meters"] as? Number)?.toInt() ?: 0,
                weight = (item["weight"] as? Number)?.toInt() ?: 1,
                enabled = item["enabled"] as? Boolean ?: true,
            )
        }
        val routeLoads = this["routeLoads"].asMapList().map { it.toWeightedItem() }
        val obstacles = this["obstacles"].asMapList().map { it.toWeightedItem() }
        return WorkoutProfile(
            name = this["name"] as? String ?: "Community-Profil",
            distances = distances,
            routeLoads = routeLoads,
            obstacles = obstacles,
        )
    }

    private fun Map<String, Any?>.toWeightedItem() = WeightedItem(
        name = this["name"] as? String ?: "",
        weight = (this["weight"] as? Number)?.toInt() ?: 1,
        enabled = this["enabled"] as? Boolean ?: true,
        detail = this["detail"] as? String ?: "",
        isNone = this["isNone"] as? Boolean ?: false,
    )

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asStringMap(): Map<String, Any?>? = this as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMapList(): List<Map<String, Any?>> =
        (this as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()

    private fun Any?.asStringList(): List<String> =
        (this as? List<*>)?.mapNotNull { it as? String }.orEmpty()

    companion object {
        private const val PUBLISHED = "published_profiles"
        private const val SUBMISSIONS = "profile_submissions"
        private const val ROLES = "community_roles"
        private const val USERS = "community_users"
    }
}
