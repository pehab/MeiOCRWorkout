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
import de.haberland.meiocrworkout.domain.model.ProfileSubmission
import de.haberland.meiocrworkout.domain.model.WeightedItem
import de.haberland.meiocrworkout.domain.model.WorkoutProfile
import java.util.UUID
import kotlinx.coroutines.tasks.await

class CommunityRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : CommunityDataSource {
    override suspend fun signInWithGoogle(activity: Activity): CommunityUser {
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

    override fun signOut() {
        auth.signOut()
    }

    override suspend fun loadCurrentUser(): CommunityUser? {
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

    override suspend fun loadPublishedProfiles(): List<CommunityProfile> =
        firestore.collection(PUBLISHED)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(500)
            .get()
            .await()
            .documents
            .mapNotNull(::toCommunityProfile)

    override suspend fun loadOwnSubmissions(): List<ProfileSubmission> {
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

    override suspend fun loadPendingSubmissions(): List<ProfileSubmission> =
        firestore.collection(SUBMISSIONS)
            .whereEqualTo("status", "pending")
            .orderBy("submittedAt", Query.Direction.ASCENDING)
            .limit(100)
            .get()
            .await()
            .documents
            .mapNotNull(::toSubmission)

    override suspend fun submitProfile(
        profile: WorkoutProfile,
        description: String,
        location: String,
        tags: List<String>,
    ) {
        val user = auth.currentUser ?: error("Für das Veröffentlichen ist eine Anmeldung nötig.")
        val targetProfileId = "${user.uid}_${profile.id}"
        CommunityDocuments.requireOwnedTarget(user.uid, targetProfileId)
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
        requireNotNull(CommunityDocuments.submission("new", data, 0L)) {
            "Das Profil enthält ungültige oder zu große Felder."
        }
        firestore.collection(SUBMISSIONS).add(data).await()
    }

    override suspend fun approveSubmission(submissionId: String) {
        val moderator = requireModerator()
        val submissionRef = firestore.collection(SUBMISSIONS).document(submissionId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(submissionRef)
            check(snapshot.exists()) { "Einreichung nicht gefunden." }
            check(snapshot.getString("status") == "pending") { "Die Einreichung ist nicht mehr offen." }

            val submission = requireNotNull(toSubmission(snapshot)) { "Die Einreichung enthält ungültige Daten." }
            val publishedRef = firestore.collection(PUBLISHED).document(submission.targetProfileId)
            val existing = transaction.get(publishedRef)
            CommunityDocuments.requirePublishOwner(
                submission.ownerUid, submission.targetProfileId, existing.exists(), existing.get("ownerUid"),
            )
            val published = mapOf<String, Any?>(
                "ownerUid" to submission.ownerUid,
                "creatorName" to submission.creatorName,
                "description" to submission.description,
                "location" to submission.location,
                "tags" to submission.tags,
                "formatVersion" to 1,
                "profile" to submission.profile.toFirestoreMap(),
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

    override suspend fun rejectSubmission(submissionId: String, note: String) {
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

    override suspend fun setModerator(email: String, enabled: Boolean) {
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

    override fun importCopy(source: WorkoutProfile): WorkoutProfile = source.copy(
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
        val data = doc.data ?: return null
        val updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.time ?: return null
        return CommunityDocuments.published(doc.id, data, updatedAt)
    }

    private fun toSubmission(doc: DocumentSnapshot): ProfileSubmission? {
        val data = doc.data ?: return null
        val submittedAt = (data["submittedAt"] as? Timestamp)?.toDate()?.time ?: return null
        return CommunityDocuments.submission(doc.id, data, submittedAt)
    }

    companion object {
        private const val PUBLISHED = "published_profiles"
        private const val SUBMISSIONS = "profile_submissions"
        private const val ROLES = "community_roles"
        private const val USERS = "community_users"
    }
}
