package com.blinkng.shared.ai

enum class BlinkAiExperiencePage {
    WELCOME,
    EXPLORE,
    CHAT,
}

data class BlinkAiExploreCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val mode: String,
    val starterPrompt: String,
)

object BlinkAiExperienceCatalog {
    val categories: List<BlinkAiExploreCategory> = listOf(
        BlinkAiExploreCategory(
            id = "coding",
            title = "Coding",
            subtitle = "Build, debug, explain and improve code.",
            mode = "code",
            starterPrompt = "Help me with a coding task.",
        ),
        BlinkAiExploreCategory(
            id = "education",
            title = "Education",
            subtitle = "Learn concepts with clear step-by-step help.",
            mode = "study",
            starterPrompt = "Teach me something step by step.",
        ),
        BlinkAiExploreCategory(
            id = "ideas",
            title = "Ideas",
            subtitle = "Brainstorm concepts, names and creative directions.",
            mode = "deep",
            starterPrompt = "Help me brainstorm strong ideas.",
        ),
        BlinkAiExploreCategory(
            id = "information",
            title = "Information",
            subtitle = "Ask questions and get clear explanations.",
            mode = "fast",
            starterPrompt = "I need a clear answer to a question.",
        ),
        BlinkAiExploreCategory(
            id = "writing",
            title = "Writing",
            subtitle = "Write, rewrite, summarize and polish text.",
            mode = "write",
            starterPrompt = "Help me improve some writing.",
        ),
        BlinkAiExploreCategory(
            id = "research",
            title = "Research",
            subtitle = "Investigate topics with current web sources.",
            mode = "research",
            starterPrompt = "Research a topic for me with reliable sources.",
        ),
        BlinkAiExploreCategory(
            id = "campus",
            title = "Campus",
            subtitle = "Plan study, campus activities and student tasks.",
            mode = "study",
            starterPrompt = "Help me with a campus or student task.",
        ),
        BlinkAiExploreCategory(
            id = "mathematics",
            title = "Mathematics",
            subtitle = "Work through calculations and mathematical ideas.",
            mode = "study",
            starterPrompt = "Help me understand a mathematics problem.",
        ),
        BlinkAiExploreCategory(
            id = "career",
            title = "Career",
            subtitle = "Improve CVs, applications and professional plans.",
            mode = "write",
            starterPrompt = "Help me with a career or professional task.",
        ),
        BlinkAiExploreCategory(
            id = "smart-shopping",
            title = "Smart Shopping",
            subtitle = "Compare options and make informed choices.",
            mode = "research",
            starterPrompt = "Help me compare some options before I choose.",
        ),
        BlinkAiExploreCategory(
            id = "blink-help",
            title = "BLINK Help",
            subtitle = "Understand BLINK features and how to use them.",
            mode = "fast",
            starterPrompt = "Help me use BLINK.",
        ),
        BlinkAiExploreCategory(
            id = "deep-thinking",
            title = "Deep Thinking",
            subtitle = "Break down difficult questions and decisions.",
            mode = "deep",
            starterPrompt = "Help me think carefully through a difficult question.",
        ),
    )

    fun category(id: String): BlinkAiExploreCategory? =
        categories.firstOrNull { it.id == id }
}
