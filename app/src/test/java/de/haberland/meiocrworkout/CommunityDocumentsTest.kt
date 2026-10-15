package de.haberland.meiocrworkout

import de.haberland.meiocrworkout.data.community.CommunityDocuments
import org.junit.Assert.*
import org.junit.Test

class CommunityDocumentsTest {
    private val profile = mapOf(
        "name" to "OCR",
        "distances" to listOf(mapOf("meters" to 500L, "weight" to 2L, "enabled" to true)),
        "obstacles" to listOf(mapOf("name" to "Wand", "weight" to 1L)),
    )
    private fun valid(): Map<String, Any?> = mapOf(
        "ownerUid" to "alice",
        "targetProfileId" to "alice_profile",
        "profile" to profile,
        "status" to "pending",
        "creatorName" to "Alice",
        "tags" to listOf("OCR"),
    )

    @Test fun validSubmissionAndPublishedProfileRemainReadable() {
        val submission = requireNotNull(CommunityDocuments.submission("submission", valid(), 123L))
        assertEquals("alice_profile", submission.targetProfileId)
        assertEquals(500, submission.profile.distances.single().meters)
        assertEquals(1, submission.profile.obstacles.single().weight)
        assertEquals(123L, submission.submittedAtEpochMs)
        assertNotNull(CommunityDocuments.published("alice_profile", valid(), 123L))
    }

    @Test fun foreignTargetAndPathInjectionAreRejected() {
        for (target in listOf("bob_profile", "alice_", "alice_profile/other", "", "alice2_profile")) {
            assertNull(CommunityDocuments.submission("bad", valid() + ("targetProfileId" to target), 123L))
        }
        assertNull(CommunityDocuments.published("bob_profile", valid(), 123L))
    }

    @Test fun existingPublishedOwnerMustMatchInsideTheTransaction() {
        CommunityDocuments.requirePublishOwner("alice", "alice_profile", false, null)
        CommunityDocuments.requirePublishOwner("alice", "alice_profile", true, "alice")
        for (owner in listOf("bob", null, 42)) {
            assertThrows(IllegalStateException::class.java) {
                CommunityDocuments.requirePublishOwner("alice", "alice_profile", true, owner)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommunityDocuments.requirePublishOwner("alice", "bob_profile", true, "alice")
        }
    }

    @Test fun malformedRowsDoNotHideValidRowsInTheSameQuery() {
        val rows = listOf(
            valid() + ("targetProfileId" to 42L),
            valid() + ("creatorName" to true),
            valid() + ("tags" to listOf(1L)),
            valid() + ("profile" to "not an object"),
            valid() + ("profile" to (profile + ("distances" to listOf("bad")))),
            valid(),
        )
        val decoded = rows.mapNotNull { CommunityDocuments.submission("row", it, 123L) }
        assertEquals(1, decoded.size)
        assertEquals("OCR", decoded.single().profile.name)
    }

    @Test fun overflowingAndOutOfRangeNumbersAreRejected() {
        for (meters in listOf<Number>(Long.MAX_VALUE, -1L, 5_000_000L, Double.NaN, 12.5)) {
            val bad = profile + ("distances" to listOf(mapOf("meters" to meters)))
            assertNull(CommunityDocuments.submission("bad", valid() + ("profile" to bad), 123L))
        }
        val bad = profile + ("obstacles" to listOf(mapOf("weight" to Long.MAX_VALUE)))
        assertNull(CommunityDocuments.submission("bad", valid() + ("profile" to bad), 123L))
    }

    @Test fun excessivelyLargeCollectionsAreRejected() {
        val bad = profile + ("obstacles" to List(501) { mapOf("name" to "Wand") })
        assertNull(CommunityDocuments.submission("bad", valid() + ("profile" to bad), 123L))
        assertNull(CommunityDocuments.submission("bad", valid() + ("tags" to List(13) { "OCR" }), 123L))
    }
}
