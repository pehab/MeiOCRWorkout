package de.haberland.meiocrworkout.domain.generator

import de.haberland.meiocrworkout.ui.*
import de.haberland.meiocrworkout.ui.screens.*
import de.haberland.meiocrworkout.presentation.*
import de.haberland.meiocrworkout.domain.model.*
import de.haberland.meiocrworkout.domain.generator.*
import de.haberland.meiocrworkout.data.local.*
import de.haberland.meiocrworkout.data.repository.*
import de.haberland.meiocrworkout.util.*

/**
 * Central place for the numeric bounds used across the UI and persistence layer,
 * so a limit only ever needs to change in one spot.
 */
object Limits {
    const val WEIGHT_MIN = 1
    const val WEIGHT_MAX = 20

    const val ROUNDS_MIN = 1
    const val ROUNDS_MAX = 200

    const val AMRAP_MINUTES_MIN = 1
    const val AMRAP_MINUTES_MAX = 600

    /** Newest history records retained; older ones are trimmed by [AppRepository]. */
    const val MAX_HISTORY_RECORDS = 500

    /**
     * Size of the round buffer [SessionGenerator] pre-generates for AMRAP sessions.
     * In practice nobody taps through this many rounds, but it does mean an AMRAP
     * offen session has a (very large) implicit upper bound rather than being truly
     * infinite - see the comment on WorkoutScreen's advanceRound().
     */
    const val AMRAP_ROUND_BUFFER = 500
}
