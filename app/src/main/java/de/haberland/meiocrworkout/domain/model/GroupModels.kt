package de.haberland.meiocrworkout.domain.model

data class GroupWorkoutSession(
    val profileName: String,
    val durationMinutes: Int,
    val athletes: List<GroupAthletePlan>,
)

data class GroupAthletePlan(
    val id: Int,
    val name: String,
    val rounds: List<WorkoutRound>,
)
