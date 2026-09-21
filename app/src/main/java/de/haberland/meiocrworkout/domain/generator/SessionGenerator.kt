package de.haberland.meiocrworkout.domain.generator

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object SessionGenerator {

    fun byRounds(profile: WorkoutProfile, count: Int, random: Random = Random.Default): WorkoutPlan {
        require(count > 0)
        require(hasMeaningfulItem(profile)) { "Im Profil ist kein aktives Trainingselement verfügbar." }

        val rounds = generateRounds(
            profile = profile,
            count = count,
            random = random,
            coverageWithin = count
        )
        return WorkoutPlan(
            profileId = profile.id,
            profileName = profile.name,
            mode = WorkoutMode.ROUNDS,
            rounds = rounds,
            requestedRounds = count
        )
    }

    fun byDistance(profile: WorkoutProfile, targetMeters: Int, random: Random = Random.Default): WorkoutPlan {
        require(targetMeters > 0)
        require(hasMeaningfulItem(profile)) { "Im Profil ist kein aktives Trainingselement verfügbar." }

        val activeDistances = profile.distances.filter { it.enabled }
        val positiveDistances = activeDistances.filter { it.totalMeters > 0 }
        require(positiveDistances.isNotEmpty()) { "Für den Distanzmodus ist mindestens eine aktive Strecke > 0 m nötig." }

        val picked = mutableListOf<DistanceOption?>()
        var sum = 0
        var zeroStreak = 0
        var guard = 0

        // guard caps this at 1000 iterations as a safety net against an unreachable
        // target (e.g. only very small distances enabled). If the cap is hit before
        // targetMeters is reached, the loop just stops and the plan comes back shorter
        // than requested, with no error surfaced to the caller - deliberate best-effort
        // behaviour, but worth knowing if a shorter-than-requested plan is ever reported.
        while (sum < targetMeters && guard++ < 1000) {
            val remaining = targetMeters - sum
            val allowZero = zeroStreak < 2 && activeDistances.any { it.totalMeters == 0 }
            val candidatePool = if (allowZero) activeDistances else positiveDistances

            val pickedDistance = if (remaining <= positiveDistances.maxOf { it.totalMeters }) {
                val closestPositive = positiveDistances.sortedBy { abs(remaining - it.totalMeters) }
                val bestDiff = abs(remaining - closestPositive.first().totalMeters)
                val close = closestPositive.takeWhile { abs(remaining - it.totalMeters) <= bestDiff + 200 }
                if (allowZero && random.nextInt(100) < zeroWeightPercent(activeDistances)) {
                    activeDistances.filter { it.totalMeters == 0 }
                        .let { weightedPick(it, { x -> x.weight }, random) }
                } else {
                    weightedPick(close, { x -> x.weight }, random)
                }
            } else {
                weightedPick(candidatePool, { it.weight }, random)
            }

            picked += pickedDistance
            if (pickedDistance.totalMeters == 0) {
                zeroStreak++
            } else {
                zeroStreak = 0
                sum += pickedDistance.totalMeters
            }
        }

        val count = picked.size
        val routeSeq = adaptiveNullableSession(
            items = profile.routeLoads,
            count = count,
            random = random,
            coverageWithin = count
        )
        val obstacleSeq = adaptiveNullableSession(
            items = profile.obstacles,
            count = count,
            random = random,
            coverageWithin = count
        )

        val rounds = List(count) { i ->
            ensureNotEmpty(
                WorkoutRound(i + 1, picked[i], routeSeq[i], obstacleSeq[i]),
                profile,
                random
            )
        }

        return WorkoutPlan(
            profileId = profile.id,
            profileName = profile.name,
            mode = WorkoutMode.DISTANCE,
            rounds = rounds,
            requestedDistanceMeters = targetMeters
        )
    }

    fun byAmrapTime(profile: WorkoutProfile, minutes: Int, random: Random = Random.Default): WorkoutPlan {
        require(minutes > 0)
        require(hasMeaningfulItem(profile)) { "Im Profil ist kein aktives Trainingselement verfügbar." }
        return amrapPlan(profile, WorkoutMode.AMRAP_TIME, minutes, random)
    }

    fun byAmrapOpen(profile: WorkoutProfile, random: Random = Random.Default): WorkoutPlan {
        require(hasMeaningfulItem(profile)) { "Im Profil ist kein aktives Trainingselement verfügbar." }
        return amrapPlan(profile, WorkoutMode.AMRAP_OPEN, null, random)
    }

    private fun amrapPlan(
        profile: WorkoutProfile,
        mode: WorkoutMode,
        minutes: Int?,
        random: Random
    ): WorkoutPlan {
        // AMRAP has no known end. Generate a large buffer, but make sure all active
        // real route loads / obstacles are represented fairly early instead of only
        // being guaranteed somewhere in round 1..Limits.AMRAP_ROUND_BUFFER.
        val count = Limits.AMRAP_ROUND_BUFFER
        val realCount = max(
            activeRealItems(profile.routeLoads).size,
            activeRealItems(profile.obstacles).size
        )
        val coverageWithin = min(count, max(8, realCount * 2))
        val rounds = generateRounds(profile, count, random, coverageWithin)

        return WorkoutPlan(
            profileId = profile.id,
            profileName = profile.name,
            mode = mode,
            rounds = rounds,
            requestedDurationMinutes = minutes
        )
    }

    private fun generateRounds(
        profile: WorkoutProfile,
        count: Int,
        random: Random,
        coverageWithin: Int
    ): List<WorkoutRound> {
        val distanceSeq = adaptiveDistanceSession(profile.distances, count, random)
        val routeSeq = adaptiveNullableSession(profile.routeLoads, count, random, coverageWithin)
        val obstacleSeq = adaptiveNullableSession(profile.obstacles, count, random, coverageWithin)

        return List(count) { i ->
            ensureNotEmpty(
                WorkoutRound(i + 1, distanceSeq[i], routeSeq[i], obstacleSeq[i]),
                profile,
                random
            )
        }
    }

    /**
     * Controlled randomness:
     * - base weights still define how often an item should tend to appear
     * - after an item is used, its effective weight drops to 25 %
     * - each skipped round restores 7.5 percentage points of that multiplier
     *   (roughly the requested "20 % -> 5 % -> +1..2 percentage points/round" behaviour)
     * - real route loads / obstacles receive a small unseen bonus
     * - if enough slots exist, every active real element is guaranteed at least once
     *   within [coverageWithin]
     *
     * Consecutive repeats are still possible; they are just less likely.
     */
    private class AdaptivePicker<T>(
        items: List<T>,
        private val baseWeight: (T) -> Int,
        private val countsForCoverage: (T) -> Boolean,
        private val random: Random,
        private val coverageWithin: Int?
    ) {
        private data class State<T>(
            val item: T,
            val base: Double,
            var lastUsedRound: Int? = null,
            var usedCount: Int = 0
        )

        private val states = items.map {
            State(item = it, base = baseWeight(it).coerceAtLeast(1).toDouble())
        }

        fun pick(roundIndex: Int): T {
            require(states.isNotEmpty())

            val unseen = states.filter { it.usedCount == 0 && countsForCoverage(it.item) }
            val coverageLimit = coverageWithin?.coerceAtLeast(1)
            val roundsLeftInCoverageWindow = coverageLimit?.let { it - roundIndex }

            // Hard coverage only when mathematically necessary. This keeps the earlier
            // rounds random, including the possibility of immediate repeats.
            val pool = if (
                unseen.isNotEmpty() &&
                roundsLeftInCoverageWindow != null &&
                roundsLeftInCoverageWindow > 0 &&
                roundsLeftInCoverageWindow <= unseen.size
            ) {
                unseen
            } else {
                states
            }

            val weighted = pool.map { state ->
                state to effectiveWeight(state, roundIndex)
            }
            val total = weighted.sumOf { it.second }.coerceAtLeast(0.0001)
            var roll = random.nextDouble() * total
            val chosen = weighted.firstOrNull { (_, w) ->
                roll -= w
                roll <= 0.0
            }?.first ?: weighted.last().first

            chosen.lastUsedRound = roundIndex
            chosen.usedCount++
            return chosen.item
        }

        private fun effectiveWeight(state: State<T>, roundIndex: Int): Double {
            val repetitionMultiplier = when (val last = state.lastUsedRound) {
                null -> 1.0
                else -> {
                    val skippedRounds = (roundIndex - last - 1).coerceAtLeast(0)
                    min(1.0, 0.25 + skippedRounds * 0.075)
                }
            }

            // Unseen items are nudged upward, but not forced until the coverage window
            // would otherwise be missed.
            val coverageBonus = if (state.usedCount == 0 && countsForCoverage(state.item)) 1.35 else 1.0
            return state.base * repetitionMultiplier * coverageBonus
        }
    }

    // Invariant: every public by*() function above checks hasMeaningfulItem(profile)
    // before calling this, so the require() below should never actually fire - it's a
    // last-resort assertion, not expected user-facing validation. If a new entry point
    // into round generation is added later, it must perform that same check first.
    private fun ensureNotEmpty(round: WorkoutRound, profile: WorkoutProfile, random: Random): WorkoutRound {
        if (!round.isEmpty) return round

        // A zero-distance + no route load + no obstacle round is never allowed.
        // Prefer adding a real obstacle, then a route load, then a positive distance.
        val obstacle = activeRealItems(profile.obstacles).takeIf { it.isNotEmpty() }
            ?.let { weightedPick(it, { x -> x.weight }, random) }
        if (obstacle != null) return round.copy(obstacle = obstacle)

        val load = activeRealItems(profile.routeLoads).takeIf { it.isNotEmpty() }
            ?.let { weightedPick(it, { x -> x.weight }, random) }
        if (load != null) return round.copy(routeLoad = load)

        val distance = profile.distances.filter { it.enabled && it.totalMeters > 0 }.takeIf { it.isNotEmpty() }
            ?.let { weightedPick(it, { x -> x.weight }, random) }

        require(distance != null) { "Die Runde wäre leer und im Profil gibt es keinen Ersatz." }
        return round.copy(distance = distance)
    }

    private fun adaptiveDistanceSession(
        items: List<DistanceOption>,
        count: Int,
        random: Random
    ): List<DistanceOption?> {
        val active = items.filter { it.enabled }
        if (active.isEmpty()) return List(count) { null }
        val picker = AdaptivePicker(
            items = active,
            baseWeight = { it.weight },
            countsForCoverage = { false },
            random = random,
            coverageWithin = null
        )
        return List(count) { index -> picker.pick(index) }
    }

    private fun adaptiveNullableSession(
        items: List<WeightedItem>,
        count: Int,
        random: Random,
        coverageWithin: Int
    ): List<WeightedItem?> {
        val active = items.filter { it.enabled }
        if (active.isEmpty()) return List(count) { null }

        val realCount = active.count { !it.isNone }
        val effectiveCoverage = if (realCount == 0) null else min(count, max(realCount, coverageWithin))
        val picker = AdaptivePicker(
            items = active,
            baseWeight = { it.weight },
            countsForCoverage = { !it.isNone },
            random = random,
            coverageWithin = effectiveCoverage
        )
        return List(count) { index -> picker.pick(index).takeUnless { it.isNone } }
    }

    private fun activeRealItems(items: List<WeightedItem>) = items.filter { it.enabled && !it.isNone }

    private fun hasMeaningfulItem(profile: WorkoutProfile): Boolean =
        profile.distances.any { it.enabled && it.totalMeters > 0 } ||
            activeRealItems(profile.routeLoads).isNotEmpty() ||
            activeRealItems(profile.obstacles).isNotEmpty()

    private fun zeroWeightPercent(items: List<DistanceOption>): Int {
        val allWeight = items.sumOf { it.weight.coerceAtLeast(1) }.coerceAtLeast(1)
        val zeroWeight = items.filter { it.totalMeters == 0 }.sumOf { it.weight.coerceAtLeast(1) }
        return ((zeroWeight * 100.0) / allWeight).toInt().coerceIn(0, 60)
    }

    internal fun <T> weightedPick(items: List<T>, weight: (T) -> Int, random: Random): T {
        require(items.isNotEmpty())
        val total = items.sumOf { weight(it).coerceAtLeast(1) }
        var roll = random.nextInt(total)
        for (item in items) {
            roll -= weight(item).coerceAtLeast(1)
            if (roll < 0) return item
        }
        return items.last()
    }
}
