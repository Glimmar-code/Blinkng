package com.blinkng.shared

/**
 * Pure Connect discovery policy shared by Android and Windows.
 *
 * Ranking only considers public, user-controlled signals. Never infer
 * sensitive characteristics or use coins, XP or paid badges to buy visibility.
 * Profiles blocked or hidden by privacy policy must be filtered server-side
 * before this scorer is called.
 */
data class ConnectDiscoverySignals(
    val onlineNow: Boolean = false,
    val sameCampus: Boolean = false,
    val sameDepartment: Boolean = false,
    val alreadyFollowing: Boolean = false
)

object ConnectDiscoveryRanking {
    fun score(signals: ConnectDiscoverySignals): Int =
        (if (signals.sameDepartment) 45 else 0) +
        (if (signals.sameCampus) 25 else 0) +
        (if (signals.onlineNow) 20 else 0) -
        (if (signals.alreadyFollowing) 10 else 0)
}

/**
 * Failed sections are explicit rather than being silently shown as zero
 * listings. Display friendly labels, never backend errors or auth tokens.
 */
object ConnectDataHealth {
    val sectionNames: Map<String, String> = linkedMapOf(
        "directory" to "Connect listings",
        "students" to "Students",
        "following" to "Follow status",
        "roommates" to "Roommates",
        "mentors" to "Mentors",
        "reading" to "Reading mates",
        "agents" to "Housing agents",
        "housing" to "Housing requests",
        "challenges" to "Game challenges",
        "matches" to "Discover people",
        "inbox" to "Connect inbox",
        "game_stats" to "Game activity"
    )

    fun failedLabels(failedSections: Set<String>): List<String> =
        sectionNames.filterKeys { it in failedSections }.values.toList()

    fun message(failedSections: Set<String>): String? {
        val labels = failedLabels(failedSections)
        if (labels.isEmpty()) return null
        return if (labels.size <= 2) {
            labels.joinToString(" and ") + " couldn't refresh. Other sections are still available."
        } else {
            "${labels.size} Connect sections couldn't refresh. Other sections are still available."
        }
    }
}
