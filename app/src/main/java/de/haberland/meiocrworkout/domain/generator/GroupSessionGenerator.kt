package de.haberland.meiocrworkout.domain.generator

import de.haberland.meiocrworkout.domain.model.GroupAthletePlan
import de.haberland.meiocrworkout.domain.model.GroupParticipantConfig
import de.haberland.meiocrworkout.domain.model.GroupWorkoutSession
import de.haberland.meiocrworkout.domain.model.WorkoutPlan
import kotlin.math.roundToInt

object GroupSessionGenerator {
    fun create(
        plan: WorkoutPlan,
        participants: List<GroupParticipantConfig>,
    ): GroupWorkoutSession {
        require(participants.size in 2..20)
        require(plan.rounds.isNotEmpty())

        val athletes = participants.mapIndexed { index, participant ->
            val offset = ((index.toDouble() * plan.rounds.size) / participants.size)
                .roundToInt() % plan.rounds.size
            val rotated = plan.rounds.drop(offset) + plan.rounds.take(offset)

            GroupAthletePlan(
                id = index + 1,
                name = participant.name.trim().ifBlank { "Teilnehmer ${index + 1}" },
                colorIndex = participant.colorIndex,
                rounds = rotated,
            )
        }

        return GroupWorkoutSession(
            plan = plan,
            athletes = athletes,
        )
    }
}
