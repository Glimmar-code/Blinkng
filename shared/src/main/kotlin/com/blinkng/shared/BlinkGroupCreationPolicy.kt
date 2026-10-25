package com.blinkng.shared

/** Shared client pre-validation. Database RPC remains the source of authorization. */
object BlinkGroupCreationPolicy {
    private val uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun normalizedTitle(title: String): String {
        val clean = title.trim()
        require(clean.length in 2..80) { "Group name must contain 2–80 characters." }
        return clean
    }

    fun normalizedMembers(creatorId: String, chosen: List<String>): List<String> {
        require(uuid.matches(creatorId)) { "Sign in before creating a group." }
        val unique = chosen.map { it.trim() }.distinct().filterNot { it.equals(creatorId, true) }
        require(unique.size in 2..49) { "Choose 2–49 other group members." }
        require(unique.all(uuid::matches)) { "One group member has an invalid account ID." }
        return unique
    }
}
