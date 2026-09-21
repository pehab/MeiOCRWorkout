package de.haberland.meiocrworkout

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SessionGeneratorTest {

    // --- byRounds ---


    @Test
    fun configuredDistanceRepresentsTotalDistanceWithoutDoubling() {
        val option = DistanceOption("one-km", 1000, 1)
        assertEquals(1000, option.totalMeters)

        val round = WorkoutRound(
            number = 1,
            distance = option,
            routeLoad = null,
            obstacle = WeightedItem("obs", "Obstacle")
        )
        assertEquals(1000, round.totalMeters)

        val record = WorkoutRoundRecord(
            number = 1,
            distanceOneWayMeters = 1000,
            routeLoadName = null,
            routeLoadDetail = null,
            obstacleName = "Obstacle",
            obstacleDetail = null,
            durationMs = 60_000L
        )
        assertEquals(1000, record.totalMeters)
    }

    @Test
    fun roundPlanUsesRequestedCount() {
        val plan = SessionGenerator.byRounds(AppRepository.defaultProfile(), 10, Random(1))
        assertEquals(10, plan.rounds.size)
        assertTrue(plan.rounds.all { !it.isEmpty })
    }

    @Test
    fun zeroDistanceCanAppearWithoutCreatingEmptyRound() {
        val profile = AppRepository.defaultProfile().copy(
            distances = listOf(
                DistanceOption("zero", 0, 10),
                DistanceOption("hundred", 100, 1)
            )
        )
        val plan = SessionGenerator.byRounds(profile, 20, Random(2))
        assertTrue(plan.rounds.any { it.totalMeters == 0 })
        assertTrue(plan.rounds.all { !it.isEmpty })
    }

    @Test
    fun allActiveObstaclesAreCoveredWhenEnoughRoundsExist() {
        val profile = AppRepository.defaultProfile().copy(
            distances = listOf(DistanceOption("zero", 0, 1)),
            routeLoads = listOf(WeightedItem("none", "Keine", 10, isNone = true)),
            obstacles = listOf(
                WeightedItem("a", "A", 8),
                WeightedItem("b", "B", 2),
                WeightedItem("c", "C", 1)
            )
        )
        val plan = SessionGenerator.byRounds(profile, 6, Random(3))
        val names = plan.rounds.mapNotNull { it.obstacle?.name }.toSet()
        assertTrue(names.containsAll(setOf("A", "B", "C")))
    }

    @Test
    fun allActiveRouteLoadsAreCoveredWhenEnoughRoundsExist() {
        val profile = AppRepository.defaultProfile().copy(
            distances = listOf(DistanceOption("hundred", 100, 1)),
            routeLoads = listOf(
                WeightedItem("none", "Keine", 10, isNone = true),
                WeightedItem("burpees", "Burpees", 4),
                WeightedItem("crawl", "Bear Crawl", 1)
            ),
            obstacles = emptyList()
        )
        val plan = SessionGenerator.byRounds(profile, 5, Random(4))
        val names = plan.rounds.mapNotNull { it.routeLoad?.name }.toSet()
        assertTrue(names.containsAll(setOf("Burpees", "Bear Crawl")))
    }

    @Test
    fun repetitionsAreAllowed() {
        val profile = AppRepository.defaultProfile().copy(
            distances = listOf(DistanceOption("zero", 0, 1)),
            routeLoads = emptyList(),
            obstacles = listOf(WeightedItem("carry", "Carry", 1))
        )
        val plan = SessionGenerator.byRounds(profile, 4, Random(5))
        assertEquals(listOf("Carry", "Carry", "Carry", "Carry"), plan.rounds.map { it.obstacle?.name })
    }

    @Test
    fun byRoundsRejectsNonPositiveCount() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionGenerator.byRounds(AppRepository.defaultProfile(), 0, Random(6))
        }
    }

    @Test
    fun byRoundsRejectsProfileWithNoUsableItems() {
        val emptyProfile = AppRepository.defaultProfile().copy(
            distances = listOf(DistanceOption("zero", 0, 1)),
            routeLoads = listOf(WeightedItem("none", "Keine", 1, isNone = true)),
            obstacles = emptyList()
        )
        assertThrows(IllegalArgumentException::class.java) {
            SessionGenerator.byRounds(emptyProfile, 5, Random(7))
        }
    }

    // --- byDistance ---

    @Test
    fun byDistanceReachesOrExceedsTheTarget() {
        val profile = AppRepository.defaultProfile()
        val plan = SessionGenerator.byDistance(profile, 2000, Random(8))
        assertEquals(WorkoutMode.DISTANCE, plan.mode)
        assertEquals(2000, plan.requestedDistanceMeters)
        assertTrue(plan.plannedDistanceMeters >= 2000)
        assertTrue(plan.rounds.all { !it.isEmpty })
    }

    @Test
    fun byDistanceRejectsNonPositiveTarget() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionGenerator.byDistance(AppRepository.defaultProfile(), 0, Random(9))
        }
    }

    @Test
    fun byDistanceRejectsProfileWithoutAnyPositiveDistance() {
        val profile = AppRepository.defaultProfile().copy(
            distances = listOf(DistanceOption("zero", 0, 1))
        )
        assertThrows(IllegalArgumentException::class.java) {
            SessionGenerator.byDistance(profile, 1000, Random(10))
        }
    }

    // --- byAmrapTime / byAmrapOpen ---

    @Test
    fun byAmrapTimeFillsTheFullRoundBufferAndKeepsTheRequestedMinutes() {
        val plan = SessionGenerator.byAmrapTime(AppRepository.defaultProfile(), 45, Random(11))
        assertEquals(WorkoutMode.AMRAP_TIME, plan.mode)
        assertEquals(45, plan.requestedDurationMinutes)
        assertEquals(Limits.AMRAP_ROUND_BUFFER, plan.rounds.size)
        assertTrue(plan.isOpenEnded)
    }

    @Test
    fun byAmrapTimeRejectsNonPositiveMinutes() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionGenerator.byAmrapTime(AppRepository.defaultProfile(), 0, Random(12))
        }
    }

    @Test
    fun byAmrapOpenFillsTheFullRoundBufferWithNoRequestedDuration() {
        val plan = SessionGenerator.byAmrapOpen(AppRepository.defaultProfile(), Random(13))
        assertEquals(WorkoutMode.AMRAP_OPEN, plan.mode)
        assertNull(plan.requestedDurationMinutes)
        assertEquals(Limits.AMRAP_ROUND_BUFFER, plan.rounds.size)
    }

    @Test
    fun amrapCoversAllActiveObstaclesEarlyRatherThanOnlySomewhereInTheBuffer() {
        val profile = AppRepository.defaultProfile().copy(
            distances = listOf(DistanceOption("zero", 0, 1)),
            routeLoads = listOf(WeightedItem("none", "Keine", 10, isNone = true)),
            obstacles = listOf(
                WeightedItem("a", "A", 5),
                WeightedItem("b", "B", 1),
                WeightedItem("c", "C", 1)
            )
        )
        val plan = SessionGenerator.byAmrapOpen(profile, Random(14))
        // coverageWithin for 3 real obstacles is max(8, 3*2) = 8, so all three must appear
        // within the first 8 rounds rather than merely "somewhere in the 500-round buffer".
        val namesInFirstEight = plan.rounds.take(8).mapNotNull { it.obstacle?.name }.toSet()
        assertTrue(namesInFirstEight.containsAll(setOf("A", "B", "C")))
    }

    @Test
    fun everyModeRejectsAProfileWithNoUsableItemsAtAll() {
        val emptyProfile = AppRepository.defaultProfile().copy(
            distances = listOf(DistanceOption("zero", 0, 1)),
            routeLoads = listOf(WeightedItem("none", "Keine", 1, isNone = true)),
            obstacles = emptyList()
        )
        assertThrows(IllegalArgumentException::class.java) {
            SessionGenerator.byDistance(emptyProfile, 1000, Random(15))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SessionGenerator.byAmrapTime(emptyProfile, 30, Random(16))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SessionGenerator.byAmrapOpen(emptyProfile, Random(17))
        }
    }
}
