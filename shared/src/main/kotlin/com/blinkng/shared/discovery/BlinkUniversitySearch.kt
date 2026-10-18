package com.blinkng.shared.discovery

import java.text.Normalizer
import java.util.Locale

/**
 * A single search gateway for the current bundled institution catalogue.
 * NUC updates and missing institutions are handled in Feature 8, not invented.
 */
object BlinkUniversitySearch {
    private val aliasPatterns = mapOf(
        "FUTA" to "Federal University of Technology, Akure",
        "FUTMINNA" to "Federal University of Technology, Minna",
        "FUTO" to "Federal University of Technology, Owerri",
        "UNILAG" to "University of Lagos",
        "UNILORIN" to "University of Ilorin",
        "UNIBEN" to "University of Benin",
        "OAU" to "Obafemi Awolowo University, Ile-Ife",
        "UI" to "University of Ibadan",
        "UNN" to "University of Nigeria, Nsukka",
        "ABU" to "Ahmadu Bello University, Zaria",
        "LASU" to "Lagos State University",
        "FUNAAB" to "Federal University of Agriculture, Abeokuta",
        "UNIPORT" to "University of Port Harcourt",
    )

    private fun key(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    fun canonical(name: String): String? {
        val query = key(name)
        if (query.isBlank()) return null
        val aliases = aliasPatterns.entries.firstOrNull { key(it.key) == query }
        if (aliases != null && aliases.value in BlinkNigerianUniversities.all) return aliases.value
        return BlinkNigerianUniversities.all.firstOrNull { key(it) == query }
    }

    fun search(query: String, limit: Int = 80): List<String> {
        val q = key(query)
        if (q.isBlank()) return BlinkNigerianUniversities.all.take(limit.coerceAtLeast(0))
        val alias = aliasPatterns.entries.firstOrNull { key(it.key) == q }?.value
        return BlinkNigerianUniversities.all.asSequence()
            .filter { institution ->
                key(institution).contains(q) ||
                    (alias != null && institution == alias) ||
                    aliasPatterns.entries.any { (short, full) ->
                        institution == full && key(short).contains(q) && q.length >= 3
                    }
            }
            .distinct()
            .sortedWith(compareBy<String> { if (it == alias) 0 else 1 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it })
            .take(limit.coerceAtLeast(0))
            .toList()
    }
}
