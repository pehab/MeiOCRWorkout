package de.haberland.meiocrworkout.data.local

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Room needs to know how to turn [WorkoutMode] and [WorkoutItemType] into a storable
 * column value and back. Previously the entities stored `.name` as a plain String and
 * every read site had to remember to re-parse it (WorkoutMode.valueOf(...), or a raw
 * String equality check for WorkoutItemType) - easy to get subtly wrong, and a typo or
 * enum rename would fail silently at the String level instead of at compile time.
 * Centralizing the conversion here means the entities can hold the real enum types
 * directly and every other file benefits from compiler-checked exhaustiveness.
 */
class Converters {
    @TypeConverter
    fun fromWorkoutMode(mode: WorkoutMode): String = mode.name

    @TypeConverter
    fun toWorkoutMode(value: String): WorkoutMode =
        runCatching { WorkoutMode.valueOf(value) }.getOrDefault(WorkoutMode.ROUNDS)

    @TypeConverter
    fun fromWorkoutItemType(type: WorkoutItemType): String = type.name

    @TypeConverter
    fun toWorkoutItemType(value: String): WorkoutItemType =
        // Falls back rather than throwing: one corrupted/unrecognized row failing the
        // whole query (Room parses every TypeConverter call eagerly per row) would be
        // worse than one row being mis-typed. In practice this should never trigger -
        // these two values are never renamed - but it matches the defensive style of
        // toWorkoutMode above instead of leaving this one converter free to crash.
        runCatching { WorkoutItemType.valueOf(value) }.getOrDefault(WorkoutItemType.OBSTACLE)
}

@Database(
    entities = [
        ProfileEntity::class,
        DistanceEntity::class,
        WorkoutItemEntity::class,
        WorkoutEntity::class,
        WorkoutRoundEntity::class,
        AppSettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MeiOCRDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: MeiOCRDatabase? = null

        fun getInstance(context: Context): MeiOCRDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeiOCRDatabase::class.java,
                    "meiocrworkout.db"
                )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
