package com.example.items

object BlinkItemsPolicy {
    private val defaultMilestones = intArrayOf(1_000, 2_000, 3_000, 5_000, 7_500)

    fun nextReachedMilestone(
        steps: Long,
        lastNotifiedMilestone: Int,
        goal: Int
    ): Int? {
        val safeGoal = goal.coerceAtLeast(1_000)
        val candidates = (defaultMilestones.asList() + safeGoal)
            .distinct()
            .sorted()
        return candidates.lastOrNull { it > lastNotifiedMilestone && steps >= it }
    }

    fun stepMessage(milestone: Int, goal: Int): Pair<String, String> = when {
        milestone >= goal -> "Goal reached!" to "You reached ${formatSteps(milestone)} steps today. Great work."
        milestone >= 7_500 -> "Almost there" to "${formatSteps(milestone)} steps today. Keep it going."
        milestone >= 5_000 -> "Great progress" to "${formatSteps(milestone)} steps today. You're making strong progress."
        milestone >= 3_000 -> "Good progress" to "You've reached ${formatSteps(milestone)} steps today."
        milestone >= 2_000 -> "You're moving" to "${formatSteps(milestone)} steps already. Keep building on it."
        else -> "Nice start" to "You've reached ${formatSteps(milestone)} steps today. Keep it going."
    }

    fun weatherPriority(severity: String, urgency: String): Int {
        val severityScore = when (severity.lowercase()) {
            "extreme" -> 4
            "severe" -> 3
            "moderate" -> 2
            "minor" -> 1
            else -> 0
        }
        val urgencyScore = when (urgency.lowercase()) {
            "immediate" -> 3
            "expected" -> 2
            "future" -> 1
            else -> 0
        }
        return severityScore * 10 + urgencyScore
    }

    fun formatSteps(value: Int): String = "%,d".format(value)
}
