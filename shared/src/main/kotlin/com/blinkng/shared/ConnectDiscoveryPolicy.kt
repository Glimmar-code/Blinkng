package com.blinkng.shared

/**
 * Client-side ordering of profiles already approved for discovery by the backend.
 * Visibility, block lists and age/privacy rules MUST be enforced by Supabase RLS/RPC;
 * this policy never treats profile suggestions as authorization to message someone.
 *
 * No coins, paid boosts, sensitive attributes or fabricated presence are ranking inputs.
 */
data class ConnectDiscoverySignals(
    val id: String,
    val displayName: String,
    val university: String = "",
    val department: String = "",
    val interests: List<String> = emptyList(),
    val online: Boolean = false,
    val following: Boolean = false
)

object ConnectDiscoveryPolicy {
    private fun normal(value: String): String =
        value.trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()

    private fun normalizedInterests(items: List<String>): Set<String> =
        items.asSequence()
            .map { normal(it).lowercase() }
            .filter { it.isNotEmpty() }
            .take(40)
            .toSet()

    private fun same(a: String, b: String): Boolean =
        normal(a).isNotEmpty() && normal(b).isNotEmpty() &&
            normal(a).equals(normal(b), ignoreCase = true)

    private fun sharedInterests(person: ConnectDiscoverySignals, myInterests: List<String>): List<String> {
        val mySet = normalizedInterests(myInterests)
        return person.interests.asSequence()
            .map(::normal)
            .filter { it.isNotBlank() && it.lowercase() in mySet }
            .distinctBy { it.lowercase() }
            .take(3)
            .toList()
    }

    fun score(
        value: ConnectDiscoverySignals,
        campus: String,
        department: String,
        myInterests: List<String> = emptyList()
    ): Int =
        (if (same(value.department, department)) 30 else 0) +
            (if (same(value.university, campus)) 20 else 0) +
            sharedInterests(value, myInterests).size * 14 +
            (if (value.online) 6 else 0) +
            (if (value.following) 2 else 0)

    fun reasons(
        value: ConnectDiscoverySignals,
        campus: String,
        department: String,
        myInterests: List<String> = emptyList()
    ): List<String> = buildList {
        if (same(value.department, department)) add("Same department")
        if (same(value.university, campus)) add("Same campus")
        sharedInterests(value, myInterests).firstOrNull()?.let { add("Shared interest: $it") }
        if (value.online) add("Online now")
    }

    fun <T> rank(
        candidates: List<T>,
        campus: String,
        department: String,
        myInterests: List<String> = emptyList(),
        excludedIds: Set<String> = emptySet(),
        signals: (T) -> ConnectDiscoverySignals
    ): List<T> {
        val excluded = excludedIds.map { normal(it).lowercase() }.toSet()
        val unique = LinkedHashMap<String, Pair<T, ConnectDiscoverySignals>>()
        candidates.forEach { candidate ->
            val value = signals(candidate)
            val key = normal(value.id).lowercase()
            if (key.isNotEmpty() && key !in excluded) unique.putIfAbsent(key, candidate to value)
        }
        return unique.values.sortedWith(
            compareByDescending<Pair<T, ConnectDiscoverySignals>> {
                score(it.second, campus, department, myInterests)
            }.thenBy { it.second.displayName.trim().lowercase() }
                .thenBy { it.second.id.trim().lowercase() }
        ).map { it.first }
    }
}
