package de.haberland.meiocrworkout.data.community

import de.haberland.meiocrworkout.domain.model.CommunityProfile
import de.haberland.meiocrworkout.domain.model.DistanceOption
import de.haberland.meiocrworkout.domain.model.ProfileSubmission
import de.haberland.meiocrworkout.domain.model.WeightedItem
import de.haberland.meiocrworkout.domain.model.WorkoutProfile

/** Treat every remote document as untrusted; a bad row must not break its whole query. */
internal object CommunityDocuments {
    fun requireOwnedTarget(ownerUid: String, targetId: String) {
        require(ownerUid.isNotBlank() && ownerUid.length <= 128 && '/' !in ownerUid)
        require(targetId.startsWith("${ownerUid}_") && targetId.length > ownerUid.length + 1)
        require(targetId.length <= 512 && '/' !in targetId)
    }

    fun requirePublishOwner(ownerUid: String, targetId: String, exists: Boolean, existingOwner: Any?) {
        requireOwnedTarget(ownerUid, targetId)
        check(!exists || existingOwner == ownerUid) { "Ein fremdes Community-Profil darf nicht überschrieben werden." }
    }

    fun submission(id: String, data: Map<String, Any?>, submittedAt: Long): ProfileSubmission? = decode {
        val ownerUid = data.text("ownerUid", 128)
        val targetId = data.text("targetProfileId", 512)
        requireOwnedTarget(ownerUid, targetId)
        val status = data.text("status", 20)
        require(status in setOf("pending", "approved", "rejected"))
        ProfileSubmission(
            id = id,
            targetProfileId = targetId,
            profile = profile(data["profile"].objectMap()),
            description = data.text("description", 10_000),
            location = data.text("location", 1_000),
            tags = data.tags(),
            creatorName = data.text("creatorName", 500),
            ownerUid = ownerUid,
            ownerEmail = data.text("ownerEmail", 500),
            submittedAtEpochMs = submittedAt,
            status = status,
            moderatorNote = data.text("moderatorNote", 500),
        )
    }

    fun published(id: String, data: Map<String, Any?>, updatedAt: Long): CommunityProfile? = decode {
        val ownerUid = data.text("ownerUid", 128)
        requireOwnedTarget(ownerUid, id)
        CommunityProfile(
            id = id,
            profile = profile(data["profile"].objectMap()),
            description = data.text("description", 10_000),
            location = data.text("location", 1_000),
            tags = data.tags(),
            creatorName = data.text("creatorName", 500),
            ownerUid = ownerUid,
            updatedAtEpochMs = updatedAt,
        )
    }

    private fun profile(data: Map<String, Any?>) = WorkoutProfile(
        name = data.text("name", 500),
        distances = data.objects("distances").map {
            DistanceOption(
                meters = it.integer("meters", 0, 1_000_000, 0),
                weight = it.integer("weight", 1, 20, 1),
                enabled = it.flag("enabled", true),
            )
        },
        routeLoads = data.objects("routeLoads").map(::weightedItem),
        obstacles = data.objects("obstacles").map(::weightedItem),
    )

    private fun weightedItem(data: Map<String, Any?>) = WeightedItem(
        name = data.text("name", 500),
        weight = data.integer("weight", 1, 20, 1),
        enabled = data.flag("enabled", true),
        detail = data.text("detail", 10_000),
        isNone = data.flag("isNone", false),
    )

    private inline fun <T> decode(block: () -> T): T? = try {
        block()
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun Map<String, Any?>.text(key: String, max: Int): String {
        val value = this[key] ?: return ""
        require(value is String && value.length <= max) { "Ungültiges Textfeld." }
        return value
    }

    private fun Map<String, Any?>.integer(key: String, min: Int, max: Int, default: Int): Int {
        val value = this[key] ?: return default
        require(value is Long || value is Int) { "Ungültiges Zahlenfeld." }
        val number = (value as Number).toLong()
        require(number in min.toLong()..max.toLong()) { "Zahl außerhalb des gültigen Bereichs." }
        return number.toInt()
    }

    private fun Map<String, Any?>.flag(key: String, default: Boolean): Boolean {
        val value = this[key] ?: return default
        require(value is Boolean) { "Ungültiges Wahrheitsfeld." }
        return value
    }

    private fun Map<String, Any?>.objects(key: String): List<Map<String, Any?>> {
        val value = this[key] ?: return emptyList()
        require(value is List<*> && value.size <= 500) { "Ungültige Profilliste." }
        return value.map { it.objectMap() }
    }

    private fun Map<String, Any?>.tags(): List<String> {
        val value = this["tags"] ?: return emptyList()
        require(value is List<*> && value.size <= 12) { "Ungültige Tags." }
        return value.map {
            require(it is String && it.length <= 500) { "Ungültiger Tag." }
            it
        }
    }

    private fun Any?.objectMap(): Map<String, Any?> {
        require(this is Map<*, *> && keys.all { it is String }) { "Ungültiges Profilobjekt." }
        @Suppress("UNCHECKED_CAST")
        return this as Map<String, Any?>
    }
}
