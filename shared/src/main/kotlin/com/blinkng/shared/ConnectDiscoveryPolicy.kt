package com.blinkng.shared

data class ConnectDiscoverySignals(
    val id: String,
    val displayName: String,
    val university: String = "",
    val department: String = "",
    val online: Boolean = false,
    val following: Boolean = false
)

/** Display ordering only; RLS and block checks stay server authoritative. */
object ConnectDiscoveryPolicy {
    private fun normal(value: String): String =
        value.trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()

    private fun same(a: String, b: String): Boolean =
        normal(a).isNotEmpty() && normal(b).isNotEmpty() &&
            normal(a).equals(normal(b), ignoreCase = true)

    fun score(value: ConnectDiscoverySignals, campus: String, department: String): Int =
        (if (same(value.department, department)) 30 else 0) +
            (if (same(value.university, campus)) 20 else 0) +
            (if (value.online) 10 else 0) +
            (if (value.following) 3 else 0)

    fun reasons(value: ConnectDiscoverySignals, campus: String, department: String): List<String> =
        buildList {
            if (same(value.department, department)) add("Same department")
            if (same(value.university, campus)) add("Same campus")
            if (value.online) add("Online now")
        }

    fun <T> rank(
        candidates: List<T>, campus: String, department: String,
        signals: (T) -> ConnectDiscoverySignals
    ): List<T> {
        val unique = LinkedHashMap<String, Pair<T, ConnectDiscoverySignals>>()
        candidates.forEach { item ->
            val details = signals(item)
            val id = normal(details.id).lowercase()
            if (id.isNotEmpty()) unique.putIfAbsent(id, item to details)
        }
        return unique.values.sortedWith(
            compareByDescending<Pair<T, ConnectDiscoverySignals>> {
                score(it.second, campus, department)
            }.thenBy { it.second.displayName.trim().lowercase() }
                .thenBy { it.second.id.trim().lowercase() }
        ).map { it.first }
    }
}
