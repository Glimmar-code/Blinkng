package com.blinkng.shared

/**
 * Platform-neutral profile leaderboard rank presentation.
 *
 * Rank values <= 0 mean the current leaderboard snapshot does not contain
 * a valid placement yet. Existing valid values are preserved when a newer
 * snapshot omits one side of the rank pair.
 */
data class ProfileRankSnapshot(
    val worldRank: Int = 0,
    val campusRank: Int = 0,
) {
    fun resolvedAgainst(
        existingWorldRank: Int,
        existingCampusRank: Int,
    ): ProfileRankSnapshot = ProfileRankSnapshot(
        worldRank = worldRank.takeIf { it > 0 } ?: existingWorldRank.coerceAtLeast(0),
        campusRank = campusRank.takeIf { it > 0 } ?: existingCampusRank.coerceAtLeast(0),
    )
}

fun profileRankLabel(rank: Int): String =
    if (rank > 0) "#$rank" else "—"
