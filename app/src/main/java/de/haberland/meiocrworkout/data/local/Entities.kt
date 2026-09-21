package de.haberland.meiocrworkout.data.local

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation

@Entity(tableName = "profiles", primaryKeys = ["id"])
data class ProfileEntity(
    val id: String,
    val name: String,
    val sortOrder: Int
)

@Entity(
    tableName = "distances",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class DistanceEntity(
    val id: String,
    val profileId: String,
    val meters: Int,
    val weight: Int,
    val enabled: Boolean,
    val sortOrder: Int
)

enum class WorkoutItemType { ROUTE_LOAD, OBSTACLE }

@Entity(
    tableName = "workout_items",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId"), Index(value = ["profileId", "type"])]
)
data class WorkoutItemEntity(
    val id: String,
    val profileId: String,
    val type: WorkoutItemType,
    val name: String,
    val detail: String,
    val weight: Int,
    val enabled: Boolean,
    val isNone: Boolean,
    val sortOrder: Int
)

@Entity(tableName = "workouts", primaryKeys = ["id"], indices = [Index("startedAtEpochMs")])
data class WorkoutEntity(
    val id: String,
    val startedAtEpochMs: Long,
    val profileName: String,
    val mode: WorkoutMode,
    val targetLabel: String,
    val durationMs: Long,
    val aborted: Boolean
)

@Entity(
    tableName = "workout_rounds",
    primaryKeys = ["workoutId", "number"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId")]
)
data class WorkoutRoundEntity(
    val workoutId: String,
    val number: Int,
    val distanceMeters: Int,
    val routeLoadName: String?,
    val routeLoadDetail: String?,
    val obstacleName: String?,
    val obstacleDetail: String?,
    val durationMs: Long
)

@Entity(tableName = "app_settings", primaryKeys = ["key"])
data class AppSettingEntity(
    val key: String,
    val value: String
)



data class ProfileWithChildren(
    @Embedded val profile: ProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "profileId")
    val distances: List<DistanceEntity>,
    @Relation(parentColumn = "id", entityColumn = "profileId")
    val items: List<WorkoutItemEntity>
)

data class WorkoutWithRounds(
    @Embedded val workout: WorkoutEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val rounds: List<WorkoutRoundEntity>
)
