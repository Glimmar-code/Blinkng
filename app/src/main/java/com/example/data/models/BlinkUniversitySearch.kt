package com.example.data.models

/**
 * Local, offline-first picker metadata for universities *already* in NigerianUniversities.
 *
 * State mappings are deliberately explicit, not inferred from an institution's
 * "State University" name or guessed from a city. Unmapped schools remain
 * searchable in "Other / nationwide" until their campus location is verified.
 * This catalogue is not a live NUC accreditation register.
 */
object BlinkUniversitySearch {
    const val ALL_STATES = "All states"
    const val OTHER = "Other / nationwide"

    data class Entry(val name: String, val state: String, val alias: String)

    private val stateByName = mapOf(
        "Abia State University, Uturu" to "Abia",
        "Michael Okpara University of Agriculture, Umudike" to "Abia",
        "Gregory University, Uturu" to "Abia",
        "Rhema University" to "Abia",
        "Adamawa State University, Mubi" to "Adamawa",
        "Modibbo Adama University, Yola" to "Adamawa",
        "American University of Nigeria" to "Adamawa",
        "Akwa Ibom State University" to "Akwa Ibom",
        "University of Uyo" to "Akwa Ibom",
        "Anambra State University" to "Anambra",
        "Chukwuemeka Odumegwu Ojukwu University" to "Anambra",
        "Nnamdi Azikiwe University, Awka" to "Anambra",
        "Paul University" to "Anambra",
        "Madonna University" to "Anambra",
        "Tansian University" to "Anambra",
        "Abubakar Tafawa Balewa University, Bauchi" to "Bauchi",
        "Bauchi State University, Gadau" to "Bauchi",
        "Federal University of Health Sciences, Azare" to "Bauchi",
        "Niger Delta University" to "Bayelsa",
        "University of Africa, Toru-Orua" to "Bayelsa",
        "Federal University Otuoke" to "Bayelsa",
        "Benue State University" to "Benue",
        "Federal University of Agriculture, Makurdi" to "Benue",
        "Joseph Sarwuan Tarka University, Makurdi" to "Benue",
        "Borno State University" to "Borno",
        "University of Maiduguri" to "Borno",
        "Cross River University of Technology" to "Cross River",
        "University of Calabar" to "Cross River",
        "Arthur Jarvis University" to "Cross River",
        "Delta State University" to "Delta",
        "Delta State University of Science and Technology" to "Delta",
        "Dennis Osadebay University" to "Delta",
        "Oil and Gas University, Delta State" to "Delta",
        "Federal University of Petroleum Resources, Effurun" to "Delta",
        "Admiralty University of Nigeria" to "Delta",
        "Edwin Clark University" to "Delta",
        "Novena University" to "Delta",
        "Western Delta University" to "Delta",
        "Ebonyi State University" to "Ebonyi",
        "Evangel University" to "Ebonyi",
        "Ambrose Alli University, Ekpoma" to "Edo",
        "Edo State University Uzairue" to "Edo",
        "University of Benin" to "Edo",
        "Benson Idahosa University" to "Edo",
        "Igbinedion University" to "Edo",
        "Samuel Adegboyega University" to "Edo",
        "Wellspring University" to "Edo",
        "Ekiti State University" to "Ekiti",
        "Federal University Oye-Ekiti" to "Ekiti",
        "Afe Babalola University" to "Ekiti",
        "Enugu State University of Science and Technology" to "Enugu",
        "University of Nigeria, Nsukka" to "Enugu",
        "Caritas University" to "Enugu",
        "Godfrey Okoye University" to "Enugu",
        "Renaissance University" to "Enugu",
        "Gombe State University" to "Gombe",
        "Federal University of Education, Gombe" to "Gombe",
        "Federal University Kashere" to "Gombe",
        "Imo State University" to "Imo",
        "Federal University of Technology, Owerri" to "Imo",
        "Hezekiah University" to "Imo",
        "Sule Lamido University" to "Jigawa",
        "Federal University Dutse" to "Jigawa",
        "Kaduna State University" to "Kaduna",
        "Ahmadu Bello University, Zaria" to "Kaduna",
        "Nigerian Defence Academy, Kaduna" to "Kaduna",
        "Federal University of Education, Zaria" to "Kaduna",
        "Greenfield University" to "Kaduna",
        "Kano State University of Science and Technology" to "Kano",
        "Yusuf Maitama Sule University" to "Kano",
        "Bayero University, Kano" to "Kano",
        "Federal University of Education, Kano" to "Kano",
        "Nigeria Police Academy, Wudil" to "Kano",
        "Al-Qalam University" to "Kano",
        "Skyline University Nigeria" to "Kano",
        "Federal University Dutsin-Ma" to "Katsina",
        "Federal University of Education, Katsina" to "Katsina",
        "Federal University of Health Sciences, Katsina" to "Katsina",
        "Kebbi State University of Science and Technology" to "Kebbi",
        "Federal University Birnin Kebbi" to "Kebbi",
        "Kogi State University, Anyigba" to "Kogi",
        "Federal University Lokoja" to "Kogi",
        "Salem University" to "Kogi",
        "Kwara State University" to "Kwara",
        "University of Ilorin" to "Kwara",
        "Al-Hikmah University" to "Kwara",
        "Landmark University" to "Kwara",
        "Summit University" to "Kwara",
        "Thomas Adewumi University" to "Kwara",
        "Lagos State University" to "Lagos",
        "University of Lagos" to "Lagos",
        "Anchor University" to "Lagos",
        "Caleb University" to "Lagos",
        "Pan-Atlantic University" to "Lagos",
        "Trinity University" to "Lagos",
        "Miva Open University" to "Lagos",
        "Christopher University" to "Lagos",
        "Nasarawa State University" to "Nasarawa",
        "Federal University Lafia" to "Nasarawa",
        "Bingham University" to "Nasarawa",
        "Mewar International University" to "Nasarawa",
        "Niger State University" to "Niger",
        "Niger State University of Education" to "Niger",
        "Niger State University of Medical Sciences" to "Niger",
        "Federal University of Technology, Minna" to "Niger",
        "Federal University of Education, Kontagora" to "Niger",
        "Olabisi Onabanjo University" to "Ogun",
        "Federal University of Agriculture, Abeokuta" to "Ogun",
        "Federal University of Education, Abeokuta" to "Ogun",
        "Babcock University" to "Ogun",
        "Bells University of Technology" to "Ogun",
        "Covenant University" to "Ogun",
        "Crawford University" to "Ogun",
        "Crescent University" to "Ogun",
        "McPherson University" to "Ogun",
        "Mountain Top University" to "Ogun",
        "Redeemer's University" to "Ogun",
        "Southwestern University" to "Ogun",
        "Hallmark University" to "Ogun",
        "Olusegun Agagu University of Science and Technology" to "Ondo",
        "Federal University of Technology, Akure" to "Ondo",
        "Federal University of Education, Ondo" to "Ondo",
        "Achievers University" to "Ondo",
        "Elizade University" to "Ondo",
        "Osun State University" to "Osun",
        "Obafemi Awolowo University, Ile-Ife" to "Osun",
        "Adeleke University" to "Osun",
        "Bowen University" to "Osun",
        "Fountain University" to "Osun",
        "Joseph Ayo Babalola University" to "Osun",
        "Kings University" to "Osun",
        "Oduduwa University" to "Osun",
        "Westland University" to "Osun",
        "Ladoke Akintola University of Technology" to "Oyo",
        "University of Ibadan" to "Oyo",
        "Ajayi Crowther University" to "Oyo",
        "Dominion University" to "Oyo",
        "Lead City University" to "Oyo",
        "Kola Daisi University" to "Oyo",
        "Precious Cornerstone University" to "Oyo",
        "Plateau State University" to "Plateau",
        "University of Jos" to "Plateau",
        "Federal University of Education, Pankshin" to "Plateau",
        "Rivers State University" to "Rivers",
        "University of Port Harcourt" to "Rivers",
        "Ignatius Ajuru University of Education" to "Rivers",
        "PAMO University of Medical Sciences" to "Rivers",
        "Wigwe University" to "Rivers",
        "Sokoto State University" to "Sokoto",
        "Usmanu Danfodiyo University, Sokoto" to "Sokoto",
        "Taraba State University" to "Taraba",
        "Federal University Wukari" to "Taraba",
        "Yobe State University" to "Yobe",
        "Federal University Gashua" to "Yobe",
        "Zamfara State University" to "Zamfara",
        "Federal University Gusau" to "Zamfara",
        "University of Abuja" to "FCT Abuja",
        "Federal University of Medical Sciences, Jabi" to "FCT Abuja",
        "African University of Science and Technology" to "FCT Abuja",
        "Ave Maria University" to "FCT Abuja",
        "Baze University" to "FCT Abuja",
        "Nile University of Nigeria" to "FCT Abuja",
        "Veritas University" to "FCT Abuja"
    )

    private val aliases = mapOf(
        "Federal University of Technology, Akure" to "FUTA",
        "University of Lagos" to "UNILAG",
        "University of Ibadan" to "UI",
        "University of Ilorin" to "UNILORIN",
        "Obafemi Awolowo University, Ile-Ife" to "OAU",
        "University of Nigeria, Nsukka" to "UNN",
        "Ahmadu Bello University, Zaria" to "ABU",
        "Federal University Oye-Ekiti" to "FUOYE",
        "University of Benin" to "UNIBEN",
        "University of Port Harcourt" to "UNIPORT",
        "University of Abuja" to "UNIABUJA",
        "Bayero University, Kano" to "BUK",
        "University of Uyo" to "UNIUYO",
        "Ladoke Akintola University of Technology" to "LAUTECH",
        "Federal University of Technology, Owerri" to "FUTO",
        "Federal University of Technology, Minna" to "FUTMINNA",
        "Federal University of Agriculture, Abeokuta" to "FUNAAB",
        "Lagos State University" to "LASU",
        "Ekiti State University" to "EKSU",
        "Kwara State University" to "KWASU",
        "Osun State University" to "UNIOSUN"
    )

    val entries: List<Entry> by lazy {
        NigerianUniversities.all.distinct().map { name ->
            Entry(name, stateByName[name] ?: OTHER, aliases[name].orEmpty())
        }
    }

    val states: List<String> by lazy {
        listOf(ALL_STATES) + entries.map { it.state }.distinct()
            .sortedWith(compareBy<String> { it == OTHER }.thenBy { it })
    }

    fun search(query: String, state: String = ALL_STATES): List<Entry> {
        val needle = query.trim().lowercase()
        return entries.asSequence()
            .filter { state == ALL_STATES || it.state == state }
            .filter {
                needle.isEmpty() || it.name.contains(needle, ignoreCase = true) ||
                    it.state.contains(needle, ignoreCase = true) ||
                    it.alias.contains(needle, ignoreCase = true)
            }
            .sortedWith(
                compareBy<Entry> {
                    when {
                        needle.isEmpty() -> 2
                        it.name.equals(needle, true) || it.alias.equals(needle, true) -> 0
                        it.name.startsWith(needle, true) || it.alias.startsWith(needle, true) -> 1
                        else -> 2
                    }
                }.thenBy { it.state == OTHER }.thenBy { it.state }.thenBy { it.name }
            )
            .toList()
    }
}
