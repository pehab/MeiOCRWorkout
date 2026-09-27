package de.haberland.meiocrworkout.domain.generator

import de.haberland.meiocrworkout.domain.model.GroupAthletePlan
import de.haberland.meiocrworkout.domain.model.GroupWorkoutSession
import de.haberland.meiocrworkout.domain.model.WorkoutProfile
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

object GroupSessionGenerator {
    fun create(
        profile: WorkoutProfile,
        participantCount: Int,
        durationMinutes: Int,
        names: List<String> = emptyList(),
        random: Random = Random.Default,
    ): GroupWorkoutSession {
        require(participantCount in 2..20)
        require(durationMinutes > 0)

        val activeStationHint = max(
            8,
            max(
                profile.obstacles.count { it.enabled && !it.isNone },
                max(
                    profile.routeLoads.count { it.enabled && !it.isNone },
                    profile.distances.count { it.enabled },
                ),
            ),
        ).coerceAtMost(20)

        val baseRounds = SessionGenerator.byRounds(
            profile = profile,
            count = activeStationHint,
            random = random,
        ).rounds

        val athletes = List(participantCount) { index ->
            val offset = ((index.toDouble() * baseRounds.size) / participantCount)
                .roundToInt() % baseRounds.size
            val rotated = baseRounds.drop(offset) + baseRounds.take(offset)
            GroupAthletePlan(
                id = index + 1,
                name = names.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Teilnehmer ${index + 1}",
                rounds = rotated,
            )
        }

        return GroupWorkoutSession(
            profileName = profile.name,
            durationMinutes = durationMinutes,
            athletes = athletes,
        )
    }
}
