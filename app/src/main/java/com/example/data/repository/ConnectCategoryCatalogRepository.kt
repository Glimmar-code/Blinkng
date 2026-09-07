package com.example.data.repository

import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * A professional Connect directory entry. The catalog controls presentation and
 * maps each entry to one of the proven Connect Hub workflows already backed by
 * Supabase (roommate, mentor, reading, agents, housing, or challenges).
 */
data class ConnectDirectoryCategory(
    val slug: String,
    val title: String,
    val description: String,
    val routeKind: String,
    val iconKey: String = "people",
    val displayOrder: Int
)

class ConnectCategoryCatalogRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val baseUrl = SupabaseConfig.url.trimEnd('/')

    /**
     * Fetches the admin-managed Connect catalog. A deterministic 20-item fallback
     * keeps navigation usable during a transient network/API failure.
     */
    suspend fun fetchCategories(): List<ConnectDirectoryCategory> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authenticatedRequest(
                "/rest/v1/connect_category_catalog" +
                    "?select=slug,title,description,route_kind,icon_key,display_order" +
                    "&is_active=eq.true&order=display_order.asc"
            ).get().build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Connect catalog request failed (${response.code})")
                }

                val json = if (raw.isBlank()) JSONArray() else JSONArray(raw)
                buildList {
                    for (index in 0 until json.length()) {
                        val item = json.getJSONObject(index)
                        val slug = item.optString("slug").trim()
                        val title = item.optString("title").trim()
                        val routeKind = item.optString("route_kind").trim().lowercase()
                        if (slug.isBlank() || title.isBlank() || routeKind.isBlank()) continue

                        add(
                            ConnectDirectoryCategory(
                                slug = slug,
                                title = title,
                                description = item.optString("description").trim(),
                                routeKind = routeKind,
                                iconKey = item.optString("icon_key", "people").trim().ifBlank { "people" },
                                displayOrder = item.optInt("display_order", index + 1)
                            )
                        )
                    }
                }
            }
        }.getOrNull()
            ?.sortedBy { it.displayOrder }
            ?.take(20)
            ?.takeIf { it.size == 20 }
            ?: defaultCategories()
    }

    private fun authenticatedRequest(path: String): Request.Builder {
        val token = SupabaseService.accessToken()
            ?: throw IllegalStateException("Please sign in again.")

        return Request.Builder()
            .url(baseUrl + path)
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
    }

    companion object {
        fun defaultCategories(): List<ConnectDirectoryCategory> = listOf(
            ConnectDirectoryCategory(
                slug = "roommates",
                title = "Roommates",
                description = "Find compatible students who are actively looking to share accommodation.",
                routeKind = "roommate",
                iconKey = "home",
                displayOrder = 1
            ),
            ConnectDirectoryCategory(
                slug = "mentors",
                title = "Mentors",
                description = "Connect with senior students and skilled peers who can guide you.",
                routeKind = "mentor",
                iconKey = "school",
                displayOrder = 2
            ),
            ConnectDirectoryCategory(
                slug = "reading_mates",
                title = "Reading Mates",
                description = "Meet focused study partners for courses, revision and exam preparation.",
                routeKind = "reading",
                iconKey = "book",
                displayOrder = 3
            ),
            ConnectDirectoryCategory(
                slug = "housing_agents",
                title = "Verified Housing Agents",
                description = "Browse reviewed housing agents and start a direct conversation safely.",
                routeKind = "agents",
                iconKey = "verified",
                displayOrder = 4
            ),
            ConnectDirectoryCategory(
                slug = "accommodation_requests",
                title = "Accommodation Requests",
                description = "See students who need accommodation and available housing support.",
                routeKind = "housing",
                iconKey = "apartment",
                displayOrder = 5
            ),
            ConnectDirectoryCategory(
                slug = "study_partners",
                title = "Study Partners",
                description = "Find accountability-focused students who want a consistent study routine.",
                routeKind = "reading",
                iconKey = "book",
                displayOrder = 6
            ),
            ConnectDirectoryCategory(
                slug = "project_teammates",
                title = "Project Teammates",
                description = "Discover students to collaborate with on coursework and practical projects.",
                routeKind = "reading",
                iconKey = "people",
                displayOrder = 7
            ),
            ConnectDirectoryCategory(
                slug = "skill_exchange",
                title = "Skill Exchange",
                description = "Teach what you know and connect with people who can teach you something new.",
                routeKind = "mentor",
                iconKey = "school",
                displayOrder = 8
            ),
            ConnectDirectoryCategory(
                slug = "career_guidance",
                title = "Career Guidance",
                description = "Meet experienced peers for CV, portfolio, interview and career direction support.",
                routeKind = "mentor",
                iconKey = "school",
                displayOrder = 9
            ),
            ConnectDirectoryCategory(
                slug = "internship_network",
                title = "Internship Network",
                description = "Connect with students preparing for internships, SIWES and early-career opportunities.",
                routeKind = "mentor",
                iconKey = "people",
                displayOrder = 10
            ),
            ConnectDirectoryCategory(
                slug = "research_partners",
                title = "Research Partners",
                description = "Find collaborators for surveys, data collection, papers and academic research.",
                routeKind = "reading",
                iconKey = "book",
                displayOrder = 11
            ),
            ConnectDirectoryCategory(
                slug = "founders_builders",
                title = "Founders & Builders",
                description = "Meet students building startups, products, communities and campus ventures.",
                routeKind = "mentor",
                iconKey = "people",
                displayOrder = 12
            ),
            ConnectDirectoryCategory(
                slug = "freelance_collaborators",
                title = "Freelance Collaborators",
                description = "Network with creatives, developers and service providers for legitimate projects.",
                routeKind = "mentor",
                iconKey = "people",
                displayOrder = 13
            ),
            ConnectDirectoryCategory(
                slug = "campus_communities",
                title = "Campus Communities",
                description = "Discover people with shared academic, creative and professional interests.",
                routeKind = "reading",
                iconKey = "people",
                displayOrder = 14
            ),
            ConnectDirectoryCategory(
                slug = "event_partners",
                title = "Event Partners",
                description = "Find dependable partners for academic, media, club and campus events.",
                routeKind = "reading",
                iconKey = "people",
                displayOrder = 15
            ),
            ConnectDirectoryCategory(
                slug = "accountability_partners",
                title = "Accountability Partners",
                description = "Connect with peers who can help you stay consistent with goals and deadlines.",
                routeKind = "reading",
                iconKey = "book",
                displayOrder = 16
            ),
            ConnectDirectoryCategory(
                slug = "alumni_network",
                title = "Alumni & Senior Network",
                description = "Use the mentor network to reach experienced students and graduates for guidance.",
                routeKind = "mentor",
                iconKey = "school",
                displayOrder = 17
            ),
            ConnectDirectoryCategory(
                slug = "course_tutors",
                title = "Course Tutors",
                description = "Find students offering subject-specific explanations, revision help and tutoring.",
                routeKind = "mentor",
                iconKey = "school",
                displayOrder = 18
            ),
            ConnectDirectoryCategory(
                slug = "relocation_support",
                title = "Housing & Relocation Support",
                description = "Get help navigating accommodation needs, locations and verified housing options.",
                routeKind = "housing",
                iconKey = "apartment",
                displayOrder = 19
            ),
            ConnectDirectoryCategory(
                slug = "game_challenges",
                title = "Game Challenges",
                description = "Connect through quick friendly challenge games and pending invitations.",
                routeKind = "challenges",
                iconKey = "games",
                displayOrder = 20
            )
        )
    }
}
