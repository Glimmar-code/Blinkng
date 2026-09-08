from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_exact_count(path: str, old: str, new: str, expected: int) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"Expected {expected} matches in {path}, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


POST = "app/src/main/java/com/example/data/models/PostModel.kt"
SUPABASE = "app/src/main/java/com/example/data/supabase/SupabaseService.kt"
STORY_BAR = "app/src/main/java/com/example/ui/components/StoryBar.kt"
STORY_VIEWER = "app/src/main/java/com/example/ui/components/StoryViewerDialog.kt"
MESSAGES = "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"

# Keep the username as the stable routing identity, but carry a separate real display name.
replace_once(
    POST,
    "    val verificationBadge: VerificationBadge = VerificationBadge.NONE,\n    val isVip: Boolean = false\n)\n\ndata class ActivityItem(",
    "    val verificationBadge: VerificationBadge = VerificationBadge.NONE,\n    val isVip: Boolean = false,\n    val displayName: String = \"\"\n) {\n    val displayLabel: String\n        get() = displayName.trim().ifBlank { username.trim().removePrefix(\"@\") }\n}\n\ndata class ActivityItem("
)

# Supabase already fetches the creator profile for every story, so reuse that full name.
replace_once(
    SUPABASE,
    "                        username = prof?.username ?: u,\n                        avatar = prof?.avatarUrl.orEmpty(),",
    "                        username = prof?.username ?: u,\n                        avatar = prof?.avatarUrl.orEmpty(),\n                        displayName = prof?.fullName.orEmpty(),"
)

# Story rail: all three username usages are visual/accessibility labels, not routing.
replace_exact_count(STORY_BAR, "story.username", "story.displayLabel", 3)

# Fullscreen story viewer: preserve username for profile routing and replies; use display name for labels.
replace_once(
    STORY_VIEWER,
    '                contentDescription = "Story by ${currentStory.username}",',
    '                contentDescription = "Story by ${currentStory.displayLabel}",'
)
replace_once(
    STORY_VIEWER,
    "                        contentDescription = currentStory.username,",
    "                        contentDescription = currentStory.displayLabel,"
)
replace_once(
    STORY_VIEWER,
    "                                text = currentStory.username,",
    "                                text = currentStory.displayLabel,"
)
replace_once(
    STORY_VIEWER,
    '                                    text = "Send message to ${currentStory.username}...",',
    '                                    text = "Send message to ${currentStory.displayLabel}...",'
)

# Message Matches: carry verification state and show Story real names instead of handles.
replace_once(
    MESSAGES,
    "    val isOnline: Boolean,\n    val hasUnseenStory: Boolean,\n    val conversation: ChatConversation? = null,",
    "    val isOnline: Boolean,\n    val hasUnseenStory: Boolean,\n    val verificationBadge: VerificationBadge = VerificationBadge.NONE,\n    val conversation: ChatConversation? = null,"
)
replace_once(
    MESSAGES,
    "                isOnline = conversation.isOnline,\n                hasUnseenStory = false,\n                conversation = conversation",
    "                isOnline = conversation.isOnline,\n                hasUnseenStory = false,\n                verificationBadge = when {\n                    conversation.verificationBadge != VerificationBadge.NONE -> conversation.verificationBadge\n                    conversation.isVerified -> VerificationBadge.BLUE\n                    else -> VerificationBadge.NONE\n                },\n                conversation = conversation"
)
replace_once(
    MESSAGES,
    "                    name = story.username.removePrefix(\"@\"),\n                    username = normalized,\n                    avatar = story.avatar,\n                    isOnline = false,\n                    hasUnseenStory = story.hasUnseen,\n                    story = story",
    "                    name = story.displayLabel,\n                    username = normalized,\n                    avatar = story.avatar,\n                    isOnline = false,\n                    hasUnseenStory = story.hasUnseen,\n                    verificationBadge = story.verificationBadge,\n                    story = story"
)
replace_once(
    MESSAGES,
    "        Text(\n            text = person.name.substringBefore(\" \").take(10),\n            color = palette.textSecondary,\n            fontSize = 10.sp,\n            fontWeight = FontWeight.Medium,\n            maxLines = 1,\n            overflow = TextOverflow.Ellipsis\n        )",
    "        Row(verticalAlignment = Alignment.CenterVertically) {\n            Text(\n                text = person.name.substringBefore(\" \").take(10),\n                color = palette.textSecondary,\n                fontSize = 10.sp,\n                fontWeight = FontWeight.Medium,\n                maxLines = 1,\n                overflow = TextOverflow.Ellipsis,\n                modifier = Modifier.weight(1f, fill = false)\n            )\n            if (person.verificationBadge != VerificationBadge.NONE) {\n                VerificationDot(person.verificationBadge)\n            }\n        }"
)

print("Applied verified Story display-name consistency patch.")
