package com.farias.prismajam.data

import androidx.annotation.StringRes
import com.farias.prismajam.R
import kotlin.random.Random

enum class MissionType(@StringRes val textRes: Int) {
    LEVELS(R.string.mission_levels),
    BLOCKS(R.string.mission_blocks),
    BIG_BLOCKS(R.string.mission_big_blocks),
    FAST_WINS(R.string.mission_fast_wins),
    STREAK(R.string.mission_streak),
}

data class Mission(val type: MissionType, val target: Int, val reward: Int)

/** Three daily missions, the same for every player on the same day. */
object Missions {
    const val COUNT = 3

    fun forDay(day: Int): List<Mission> {
        val rnd = Random(day * 31L + 11)
        return MissionType.entries.shuffled(rnd).take(COUNT).map { type ->
            val tier = rnd.nextInt(3)
            val target = when (type) {
                MissionType.LEVELS -> listOf(2, 4, 6)[tier]
                MissionType.BLOCKS -> listOf(25, 50, 90)[tier]
                MissionType.BIG_BLOCKS -> listOf(5, 10, 18)[tier]
                MissionType.FAST_WINS -> listOf(1, 2, 3)[tier]
                MissionType.STREAK -> listOf(2, 3, 5)[tier]
            }
            Mission(type, target, listOf(30, 45, 70)[tier])
        }
    }
}
