package de.haberland.meiocrworkout

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

/**
 * Plain in-memory stand-in for [AppRepository], used only in tests. Mirrors the real
 * repository's observable behaviour closely enough for AppViewModel tests (selected-id
 * persistence, newest-first history with a MAX_HISTORY_RECORDS cap) without touching
 * Room or a real Context.
 */
class FakeWorkoutRepository(
    initialProfiles: List<WorkoutProfile> = listOf(AppRepository.defaultProfile()),
    initialSelectedProfileId: String? = null,
    initialHistory: List<WorkoutRecord> = emptyList()
) : WorkoutRepository {

    private var profiles: List<WorkoutProfile> = initialProfiles
    private var selectedProfileId: String? = initialSelectedProfileId
    private var history: List<WorkoutRecord> = initialHistory

    var initializeCallCount = 0
        private set

    override suspend fun initialize() {
        initializeCallCount++
    }

    override suspend fun loadProfiles(): List<WorkoutProfile> = profiles

    override suspend fun saveProfiles(profiles: List<WorkoutProfile>) {
        this.profiles = profiles
    }

    override suspend fun loadSelectedProfileId(): String? = selectedProfileId

    override suspend fun saveSelectedProfileId(id: String) {
        selectedProfileId = id
    }

    override suspend fun loadHistory(): List<WorkoutRecord> = history

    override suspend fun addHistory(record: WorkoutRecord): List<WorkoutRecord> {
        history = (listOf(record) + history).distinctBy { it.id }.take(Limits.MAX_HISTORY_RECORDS)
        return history
    }

    override suspend fun deleteHistory(id: String): List<WorkoutRecord> {
        history = history.filterNot { it.id == id }
        return history
    }

    override suspend fun clearHistory() {
        history = emptyList()
    }
}
