package de.haberland.meiocrworkout.domain.model

data class GroupWorkoutSession(
    val plan: WorkoutPlan,
    val athletes: List<GroupAthletePlan>,
) {
    val profileName: String get() = plan.profileName
}

data class GroupAthletePlan(
    val id: Int,
    val name: String,
    val rounds: List<WorkoutRound>,
)
