package com.blinkng.shared.discovery

import java.text.Normalizer
import java.util.Locale

/**
 * One shared directory for Android, Windows and every campus picker.
 * Alias normalization preserves existing user university names.
 * NUC references: https://enuc.nuc.edu.ng/nus (reviewed 2026-10-18).
 */
object BlinkUniversitySearch {
    private val aliases: Map<String, String> = mapOf(
        "FUTA" to "Federal University of Technology, Akure",
        "FUT AKURE" to "Federal University of Technology, Akure",
        "FEDERAL UNIVERSITY OF TECHNOLOGY AKURE" to "Federal University of Technology, Akure",
        "FUTMINNA" to "Federal University of Technology, Minna",
        "FUT MINNA" to "Federal University of Technology, Minna",
        "FUTO" to "Federal University of Technology, Owerri",
        "UNILAG" to "University of Lagos",
        "UNILORIN" to "University of Ilorin",
        "UNIBEN" to "University of Benin",
        "OAU" to "Obafemi Awolowo University, Ile-Ife",
        "UI" to "University of Ibadan",
        "UNN" to "University of Nigeria, Nsukka",
        "ABU" to "Ahmadu Bello University, Zaria",
        "LASU" to "Lagos State University",
        "LASUED" to "Lagos University of Education",
        "LASUSTECH" to "Lagos University of Science and Technology",
        "FUNAAB" to "Federal University of Agriculture, Abeokuta",
        "UNIPORT" to "University of Port Harcourt",
        "UNIZIK" to "Nnamdi Azikiwe University, Awka",
        "BUK" to "Bayero University, Kano",
        "FUPRE" to "Federal University of Petroleum Resources, Effurun",
        "FUD" to "Federal University Dutse",
        "FUOYE" to "Federal University Oye-Ekiti",
        "FULOkoja" to "Federal University Lokoja",
        "FUKASHERE" to "Federal University Kashere",
        "FUGUS" to "Federal University Gusau",
        "NOUN" to "National Open University of Nigeria",
        "AAUA" to "Adekunle Ajasin University, Akungba-Akoko",
        "OOU" to "Olabisi Onabanjo University",
        "LAUTECH" to "Ladoke Akintola University of Technology",
        "UNIOSUN" to "Osun State University",
        "UNICAL" to "University of Calabar",
        "UNIUYO" to "University of Uyo",
        "UNIJOS" to "University of Jos",
        "UNIMAID" to "University of Maiduguri",
        "RSU" to "Rivers State University",
        "KWASU" to "Kwara State University",
        "EKSU" to "Ekiti State University",
        "ESUT" to "Enugu State University of Science and Technology",
        "DELSU" to "Delta State University",
        "COOU" to "Chukwuemeka Odumegwu Ojukwu University",
        "KASU" to "Kaduna State University",
        "NSUK" to "Nasarawa State University",
        "AKSU" to "Akwa Ibom State University",
        // Historical/former names. Never create a duplicate campus from these.
        "Federal University of Technology Akure" to "Federal University of Technology, Akure",
        "Kano University of Science and Technology Wudil" to "Kano State University of Science and Technology",
        "Kogi State University Anyigba" to "Kogi State University, Anyigba",
        "Anambra State University Uli" to "Chukwuemeka Odumegwu Ojukwu University",
        "Federal University Oye Ekiti" to "Federal University Oye-Ekiti",
    )

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private val names by lazy { BlinkNigerianUniversities.records }
    private val indexed by lazy { names.associateBy { normalize(it.name) } }
    private val normalizedAliases by lazy {
        aliases.entries.mapNotNull { (alias, canonical) ->
            indexed[normalize(canonical)]?.let { normalize(alias) to it }
        }.toMap()
    }

    fun canonical(name: String): String? = record(name)?.name

    fun record(nameOrAlias: String?): BlinkNigerianUniversities.University? {
        val value = normalize(nameOrAlias.orEmpty())
        if (value.isBlank()) return null
        if (nameOrAlias?.startsWith("ng-university:") == true) {
            return BlinkNigerianUniversities.byId[nameOrAlias]
        }
        return indexed[value] ?: normalizedAliases[value]
    }

    fun idOf(nameOrAlias: String?): String? = record(nameOrAlias)?.id

    /** Campus comparisons remain correct for older profiles storing an acronym. */
    fun sameCampus(first: String?, second: String?): Boolean {
        val a = idOf(first)
        val b = idOf(second)
        return a != null && a == b
    }

    fun searchRecords(query: String, limit: Int = 80): List<BlinkNigerianUniversities.University> {
        if (limit <= 0) return emptyList()
        val q = normalize(query)
        if (q.isBlank()) return names.take(limit)
        val aliasesMatched = normalizedAliases.entries
            .asSequence()
            .filter { (alias, _) -> alias == q || (q.length >= 3 && alias.startsWith(q)) }
            .map { it.value.id }
            .toSet()
        return names.asSequence()
            .filter { record ->
                val name = normalize(record.name)
                name.contains(q) ||
                    record.id in aliasesMatched ||
                    // Fuzzy match words only, never fabricate unknown universities.
                    (q.length >= 5 && name.split(' ').any { word ->
                        word.length >= 5 && withinOneEdit(word, q)
                    })
            }
            .sortedWith(
                compareBy<BlinkNigerianUniversities.University> {
                    when {
                        it.id in aliasesMatched -> 0
                        normalize(it.name) == q -> 1
                        normalize(it.name).startsWith(q) -> 2
                        else -> 3
                    }
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            .take(limit)
            .toList()
    }

    fun search(query: String, limit: Int = 80): List<String> =
        searchRecords(query, limit).map { it.name }

    /** Tiny typo tolerance bounded to one edit (fast for an offline catalogue). */
    private fun withinOneEdit(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var mismatches = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++mismatches > 1) return false
            if (a.length >= b.length) i++
            if (b.length >= a.length) j++
        }
        return mismatches + (a.length - i) + (b.length - j) <= 1
    }
}
