package de.haberland.meiocrworkout.data.repository

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

/**
 * Everything [AppViewModel] needs from persistence, as an interface rather than the
 * concrete [AppRepository] class. This is what makes AppViewModel unit-testable without
 * Room or a real Context: tests inject a small in-memory FakeWorkoutRepository instead
 * (see app/src/test/.../FakeWorkoutRepository.kt) that implements this same contract.
 */
interface WorkoutRepository {
    suspend fun initialize()

    suspend fun loadProfiles(): List<WorkoutProfile>
    suspend fun saveProfiles(profiles: List<WorkoutProfile>)

    suspend fun loadSelectedProfileId(): String?
    suspend fun saveSelectedProfileId(id: String)

    suspend fun loadHistory(): List<WorkoutRecord>
    suspend fun addHistory(record: WorkoutRecord): List<WorkoutRecord>
    suspend fun deleteHistory(id: String): List<WorkoutRecord>
    suspend fun clearHistory()
}
