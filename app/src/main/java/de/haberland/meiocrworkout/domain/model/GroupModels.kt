package de.haberland.meiocrworkout.domain.model

data class GroupWorkoutSession(
    val plan: WorkoutPlan,
    val athletes: List<GroupAthletePlan>,
) {
    val profileName: String get() = plan.profileName
}

data class GroupParticipantConfig(
    val name: String = "",
    val colorIndex: Int = 0,
)

data class GroupAthletePlan(
    val id: Int,
    val name: String,
    val colorIndex: Int,
    val rounds: List<WorkoutRound>,
)
