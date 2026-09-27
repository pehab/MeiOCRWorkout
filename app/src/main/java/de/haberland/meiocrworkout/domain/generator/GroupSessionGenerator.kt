package de.haberland.meiocrworkout.domain.generator

import de.haberland.meiocrworkout.domain.model.GroupAthletePlan
import de.haberland.meiocrworkout.domain.model.GroupWorkoutSession
import de.haberland.meiocrworkout.domain.model.WorkoutPlan
import kotlin.math.roundToInt

object GroupSessionGenerator {
    fun create(
        plan: WorkoutPlan,
        participantCount: Int,
        names: List<String> = emptyList(),
    ): GroupWorkoutSession {
        require(participantCount in 2..20)
        require(plan.rounds.isNotEmpty())

        val athletes = List(participantCount) { index ->
            val offset = ((index.toDouble() * plan.rounds.size) / participantCount)
                .roundToInt() % plan.rounds.size
            val rotated = plan.rounds.drop(offset) + plan.rounds.take(offset)

            GroupAthletePlan(
                id = index + 1,
                name = names.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Teilnehmer ${index + 1}",
                rounds = rotated,
            )
        }

        return GroupWorkoutSession(
            plan = plan,
            athletes = athletes,
        )
    }
}
