package de.haberland.meiocrworkout.data.repository

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import android.content.Context
import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppRepository(context: Context) : WorkoutRepository {
    private val appContext = context.applicationContext
    private val db = MeiOCRDatabase.getInstance(appContext)
    private val profileDao = db.profileDao()
    private val workoutDao = db.workoutDao()
    private val settingsDao = db.settingsDao()
    private val writeMutex = Mutex()

    /**
     * Seeds the default profile on first launch. Idempotent by construction - once any
     * profile exists, the count check below is false and this is a no-op - so there is
     * no separate "already initialized" flag to maintain.
     *
     * An earlier version had one, plus a chunk of SharedPreferences-JSON import code to
     * carry MVP 0.5 installs forward into this Room-based version. The app has never
     * actually shipped, so there is no installed base and nothing to migrate - that
     * whole one-time-import path was dead weight and has been removed rather than kept
     * "just in case".
     */
    override suspend fun initialize() {
        if (profileDao.countProfiles() == 0) {
            db.withTransaction { replaceProfilesInternal(listOf(defaultProfile())) }
        }
    }

    override suspend fun loadProfiles(): List<WorkoutProfile> {
        val profiles = profileDao.getProfilesWithChildren().map { relation ->
            WorkoutProfile(
                id = relation.profile.id,
                name = relation.profile.name,
                distances = relation.distances.sortedBy { it.sortOrder }.map {
                    DistanceOption(
                        id = it.id,
                        oneWayMeters = it.oneWayMeters,
                        weight = it.weight,
                        enabled = it.enabled
                    )
                },
                routeLoads = relation.items
                    .filter { it.type == WorkoutItemType.ROUTE_LOAD }
                    .sortedBy { it.sortOrder }
                    .map { it.toModel() },
                obstacles = relation.items
                    .filter { it.type == WorkoutItemType.OBSTACLE }
                    .sortedBy { it.sortOrder }
                    .map { it.toModel() }
            )
        }
        return profiles.ifEmpty { listOf(defaultProfile()) }
    }

    override suspend fun saveProfiles(profiles: List<WorkoutProfile>) = writeMutex.withLock {
        val safe = normalizeProfiles(profiles.ifEmpty { listOf(defaultProfile()) })
        db.withTransaction { replaceProfilesInternal(safe) }
    }

    override suspend fun loadSelectedProfileId(): String? = settingsDao.get(KEY_SELECTED_PROFILE)

    override suspend fun saveSelectedProfileId(id: String) = writeMutex.withLock {
        settingsDao.put(AppSettingEntity(KEY_SELECTED_PROFILE, id))
    }

    override suspend fun loadHistory(): List<WorkoutRecord> = workoutDao.getWorkoutsWithRounds().map { relation ->
        val workout = relation.workout
        WorkoutRecord(
            id = workout.id,
            startedAtEpochMs = workout.startedAtEpochMs,
            profileName = workout.profileName,
            // No manual WorkoutMode.valueOf() here: the Converters TypeConverter
            // (Database.kt) already turned the stored column back into a real
            // WorkoutMode - including its own safe fallback - when Room loaded this row.
            mode = workout.mode,
            targetLabel = workout.targetLabel,
            durationMs = workout.durationMs,
            aborted = workout.aborted,
            rounds = relation.rounds.sortedBy { it.number }.map {
                WorkoutRoundRecord(
                    number = it.number,
                    distanceOneWayMeters = it.distanceOneWayMeters,
                    routeLoadName = it.routeLoadName,
                    routeLoadDetail = it.routeLoadDetail,
                    obstacleName = it.obstacleName,
                    obstacleDetail = it.obstacleDetail,
                    durationMs = it.durationMs
                )
            }
        )
    }

    override suspend fun addHistory(record: WorkoutRecord): List<WorkoutRecord> = writeMutex.withLock {
        db.withTransaction {
            insertWorkoutInternal(record)
            workoutDao.trimToNewest(Limits.MAX_HISTORY_RECORDS)
        }
        loadHistory()
    }

    override suspend fun deleteHistory(id: String): List<WorkoutRecord> = writeMutex.withLock {
        workoutDao.deleteWorkout(id)
        loadHistory()
    }

    override suspend fun clearHistory() = writeMutex.withLock {
        workoutDao.clearWorkouts()
    }

    private fun normalizeProfiles(profiles: List<WorkoutProfile>): List<WorkoutProfile> {
        val usedDistanceIds = mutableSetOf<String>()
        val usedItemIds = mutableSetOf<String>()

        return profiles.map { profile ->
            val distances = profile.distances.map { item ->
                val id = item.id.takeIf { it.isNotBlank() && usedDistanceIds.add(it) }
                    ?: UUID.randomUUID().toString().also(usedDistanceIds::add)
                item.copy(id = id)
            }

            fun normalizeItems(items: List<WeightedItem>): List<WeightedItem> = items.map { item ->
                val id = item.id.takeIf { it.isNotBlank() && usedItemIds.add(it) }
                    ?: UUID.randomUUID().toString().also(usedItemIds::add)
                item.copy(id = id)
            }

            profile.copy(
                distances = distances,
                routeLoads = normalizeItems(profile.routeLoads),
                obstacles = normalizeItems(profile.obstacles)
            )
        }
    }

    private suspend fun replaceProfilesInternal(profiles: List<WorkoutProfile>) {
        profileDao.deleteAllProfiles()
        profileDao.insertProfiles(
            profiles.mapIndexed { index, profile -> ProfileEntity(profile.id, profile.name, index) }
        )

        profiles.forEach { profile ->
            if (profile.distances.isNotEmpty()) {
                profileDao.insertDistances(profile.distances.mapIndexed { index, item ->
                    DistanceEntity(
                        id = item.id,
                        profileId = profile.id,
                        oneWayMeters = item.oneWayMeters,
                        weight = item.weight.coerceIn(Limits.WEIGHT_MIN, Limits.WEIGHT_MAX),
                        enabled = item.enabled,
                        sortOrder = index
                    )
                })
            }

            val routeItems = profile.routeLoads.mapIndexed { index, item ->
                item.toEntity(profile.id, WorkoutItemType.ROUTE_LOAD, index)
            }
            val obstacleItems = profile.obstacles.mapIndexed { index, item ->
                item.toEntity(profile.id, WorkoutItemType.OBSTACLE, index)
            }
            if (routeItems.isNotEmpty()) profileDao.insertWorkoutItems(routeItems)
            if (obstacleItems.isNotEmpty()) profileDao.insertWorkoutItems(obstacleItems)
        }
    }

    private suspend fun insertWorkoutInternal(record: WorkoutRecord) {
        workoutDao.insertWorkout(
            WorkoutEntity(
                id = record.id,
                startedAtEpochMs = record.startedAtEpochMs,
                profileName = record.profileName,
                mode = record.mode,
                targetLabel = record.targetLabel,
                durationMs = record.durationMs,
                aborted = record.aborted
            )
        )
        if (record.rounds.isNotEmpty()) {
            workoutDao.insertRounds(record.rounds.map {
                WorkoutRoundEntity(
                    workoutId = record.id,
                    number = it.number,
                    distanceOneWayMeters = it.distanceOneWayMeters,
                    routeLoadName = it.routeLoadName,
                    routeLoadDetail = it.routeLoadDetail,
                    obstacleName = it.obstacleName,
                    obstacleDetail = it.obstacleDetail,
                    durationMs = it.durationMs
                )
            })
        }
    }

    private fun WeightedItem.toEntity(profileId: String, type: WorkoutItemType, sortOrder: Int) =
        WorkoutItemEntity(
            id = id,
            profileId = profileId,
            type = type,
            name = name,
            detail = detail,
            weight = weight.coerceIn(Limits.WEIGHT_MIN, Limits.WEIGHT_MAX),
            enabled = enabled,
            isNone = isNone,
            sortOrder = sortOrder
        )

    private fun WorkoutItemEntity.toModel() = WeightedItem(
        id = id,
        name = name,
        weight = weight,
        enabled = enabled,
        detail = detail,
        isNone = isNone
    )

    companion object {
        private const val KEY_SELECTED_PROFILE = "selected_profile"

        fun duplicateProfile(profile: WorkoutProfile, name: String = "${profile.name} Kopie") = WorkoutProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            distances = profile.distances.map { it.copy(id = UUID.randomUUID().toString()) },
            routeLoads = profile.routeLoads.map { it.copy(id = UUID.randomUUID().toString()) },
            obstacles = profile.obstacles.map { it.copy(id = UUID.randomUUID().toString()) }
        )

        fun defaultProfile() = WorkoutProfile(
            id = "standard",
            name = "Standard",
            distances = listOf(
                DistanceOption("0", 0, 1),
                DistanceOption("100", 100, 2),
                DistanceOption("200", 200, 5),
                DistanceOption("300", 300, 2),
                DistanceOption("500", 500, 1)
            ),
            routeLoads = listOf(
                WeightedItem("route_none", "Keine", 4, isNone = true),
                WeightedItem("burpees", "Burpees", 3, detail = "15 Wdh."),
                WeightedItem("crawl", "Bear Crawl", 2, detail = "20-30 m"),
                WeightedItem("lunges", "Walking Lunges", 2, detail = "20 Schritte"),
                WeightedItem("broad", "Burpee Broad Jumps", 1, detail = "8-10 Wdh."),
                WeightedItem("squats", "Squats", 1, detail = "25 Wdh.")
            ),
            obstacles = listOf(
                WeightedItem("monkey", "Monkeybars", 2),
                WeightedItem("salmon", "Himmelsleiter", 1),
                WeightedItem("wheels", "Wheels", 1),
                WeightedItem("rings", "Rings", 1),
                WeightedItem("spinner", "Spinner", 1),
                WeightedItem("rope", "Battlerope", 2, detail = "3 x 40 s"),
                WeightedItem("carry", "Carry", 2, detail = "100 m")
            )
        )

        fun emptyProfile(name: String) = WorkoutProfile(
            name = name,
            distances = listOf(DistanceOption(oneWayMeters = 0, weight = 1)),
            routeLoads = listOf(WeightedItem(name = "Keine", weight = 1, isNone = true)),
            obstacles = emptyList()
        )
    }
}
