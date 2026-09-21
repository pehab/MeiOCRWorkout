package de.haberland.meiocrworkout.data.local

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ProfileDao {
    @Transaction
    @Query("SELECT * FROM profiles ORDER BY sortOrder ASC")
    suspend fun getProfilesWithChildren(): List<ProfileWithChildren>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(items: List<ProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDistances(items: List<DistanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutItems(items: List<WorkoutItemEntity>)

    @Query("DELETE FROM profiles")
    suspend fun deleteAllProfiles()

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun countProfiles(): Int
}

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY startedAtEpochMs DESC")
    suspend fun getWorkoutsWithRounds(): List<WorkoutWithRounds>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(item: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRounds(items: List<WorkoutRoundEntity>)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    @Query("DELETE FROM workouts")
    suspend fun clearWorkouts()

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun countWorkouts(): Int

    // "LIMIT -1 OFFSET :keepCount" is the standard SQLite idiom for "everything after
    // skipping the newest keepCount rows" - LIMIT requires a value, and -1 means
    // unbounded. So this deletes every workout except the keepCount most recent ones.
    @Query(
        "DELETE FROM workouts WHERE id IN (" +
            "SELECT id FROM workouts ORDER BY startedAtEpochMs DESC LIMIT -1 OFFSET :keepCount" +
        ")"
    )
    suspend fun trimToNewest(keepCount: Int)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: AppSettingEntity)
}
