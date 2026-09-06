from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> tuple[Path, str]:
    target = ROOT / path
    return target, target.read_text(encoding="utf-8")


def write(target: Path, text: str) -> None:
    target.write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    target, text = read(path)
    if new in text:
        print(f"already applied: {path}")
        return
    if old not in text:
        raise RuntimeError(f"Expected source block not found in {path}: {old[:100]!r}")
    write(target, text.replace(old, new, 1))
    print(f"patched: {path}")


def replace_many(path: str, replacements: list[tuple[str, str]]) -> None:
    target, text = read(path)
    changed = False
    for old, new in replacements:
        if old == new:
            continue
        if new in text and old not in text:
            continue
        if old not in text:
            raise RuntimeError(f"Expected source block not found in {path}: {old[:120]!r}")
        text = text.replace(old, new, 1)
        changed = True
    if changed:
        write(target, text)
        print(f"patched: {path}")
    else:
        print(f"already applied: {path}")


# 1) The root Scaffold already owns the system-bar inset. The old bottom bar applied
# navigationBarsPadding again and painted an opaque full-width backdrop, which created
# the black band and the oversized gap above 3-button navigation.
floating = "app/src/main/java/com/example/ui/components/FloatingBottomBar.kt"
replace_many(
    floating,
    [
        ("import androidx.compose.foundation.layout.navigationBarsPadding\n", ""),
        ("import androidx.compose.ui.draw.drawBehind\n", ""),
        ("import androidx.compose.ui.geometry.Offset\n", ""),
        ("import com.example.ui.theme.FeedBackground\n", ""),
        ("    val navigationBackdrop = if (isDark) FeedBackground else MaterialTheme.colorScheme.background\n", ""),
        (
            """    Box(\n        modifier = modifier\n            .fillMaxWidth()\n            // Edge-to-edge content used to remain visible through the navigation-bar inset,\n            // which mixed screen colors with the floating bar and looked like an overlap.\n            // Paint one stable backdrop first so every tab has a clean bottom edge.\n            .background(navigationBackdrop)\n            .navigationBarsPadding()\n            .padding(horizontal = 12.dp, vertical = 8.dp)\n            .drawBehind {\n                drawRect(\n                    brush = Brush.radialGradient(\n                        colors = listOf(FeedPurple.copy(alpha = 0.10f), Color.Transparent),\n                        center = Offset(size.width * 0.22f, size.height),\n                        radius = size.width * 0.46f\n                    )\n                )\n                drawRect(\n                    brush = Brush.radialGradient(\n                        colors = listOf(FeedBlue.copy(alpha = 0.08f), Color.Transparent),\n                        center = Offset(size.width * 0.82f, size.height),\n                        radius = size.width * 0.42f\n                    )\n                )\n            },\n        contentAlignment = Alignment.Center\n    ) {\n""",
            """    Box(\n        modifier = modifier\n            .fillMaxWidth()\n            // MainActivity's Scaffold already applies the navigation-bar inset. Keep this\n            // wrapper transparent so the active screen remains visible around the floating\n            // pill instead of creating a second black rectangle below the app.\n            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),\n        contentAlignment = Alignment.Center\n    ) {\n""",
        ),
        ("            shadowElevation = 12.dp,\n", "            shadowElevation = 10.dp,\n"),
        ("                    .padding(horizontal = 6.dp, vertical = 7.dp),\n", "                    .padding(horizontal = 6.dp, vertical = 5.dp),\n"),
        ("            .heightIn(min = 64.dp)\n", "            .heightIn(min = 60.dp)\n"),
    ],
)

# 2) Remove the Android 10+ three-button navigation contrast scrim. Edge-to-edge remains
# enabled and Compose/Scaffold still protects interactive content from the system buttons.
replace_once(
    "app/src/main/java/com/example/MainActivity.kt",
    """        enableEdgeToEdge()\n\n        setContent {\n""",
    """        enableEdgeToEdge()\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n            window.isNavigationBarContrastEnforced = false\n        }\n\n        setContent {\n""",
)

# 3) Make messaging black by default while preserving Pink and Light as explicit choices.
message_theme = "app/src/main/java/com/example/ui/theme/MessageTheme.kt"
replace_many(
    message_theme,
    [
        ('    DARK("dark", "Dark"),\n', '    DARK("dark", "Black"),\n'),
        ("            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: PINK\n", "            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: DARK\n"),
        (
            """private val DarkMessagePalette = MessagePalette(\n    mode = MessageThemeMode.DARK,\n    backgroundTop = Color(0xFF151720),\n    backgroundMiddle = Color(0xFF090A0F),\n    backgroundBottom = Color(0xFF05060B),\n    glass = Color(0xFF10131A),\n    glassElevated = Color(0xFF191E2C),\n    border = Color(0xFF353B4C),\n    accent = Color(0xFF8B5CF6),\n    accentSecondary = Color(0xFF6D28D9),\n    textPrimary = Color(0xFFF7F5FF),\n    textSecondary = Color(0xFFAAA9BD),\n    textMuted = Color(0xFF777A91),\n    incomingBubble = Color(0xFF202431),\n    outgoingBubble = Color(0xFF7C3AED),\n    outgoingText = Color.White\n)\n""",
            """private val DarkMessagePalette = MessagePalette(\n    mode = MessageThemeMode.DARK,\n    backgroundTop = Color(0xFF08090F),\n    backgroundMiddle = Color(0xFF030408),\n    backgroundBottom = Color(0xFF000000),\n    glass = Color(0xFF0A0D13),\n    glassElevated = Color(0xFF111620),\n    border = Color(0xFF252B38),\n    accent = Color(0xFF9B6CFF),\n    accentSecondary = Color(0xFF3B82F6),\n    textPrimary = Color(0xFFF8F7FF),\n    textSecondary = Color(0xFFB7B5C8),\n    textMuted = Color(0xFF777B8E),\n    incomingBubble = Color(0xFF171B24),\n    outgoingBubble = Color(0xFF6D3FEF),\n    outgoingText = Color.White\n)\n""",
        ),
    ],
)

# 4) Premium message-home polish: subtle staged entrance motion, a second cobalt glow,
# richer conversation cards, and correct default-theme labels.
premium = "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"
replace_many(
    premium,
    [
        ("import androidx.compose.animation.slideInHorizontally\n", "import androidx.compose.animation.slideInHorizontally\nimport androidx.compose.animation.slideInVertically\n"),
        (
            """    val startVoiceSearch = rememberSpeechInput { result -> query = result }\n\n    MessageBackground(palette) {\n""",
            """    val startVoiceSearch = rememberSpeechInput { result -> query = result }\n    var entered by remember { mutableStateOf(false) }\n    LaunchedEffect(Unit) { entered = true }\n\n    MessageBackground(palette) {\n""",
        ),
        (
            """            SearchMatchesField(\n                value = query,\n                onValueChange = { query = it },\n                onVoiceSearch = startVoiceSearch,\n                palette = palette\n            )\n\n            MatchesRail(\n                conversations = conversations,\n                stories = stories,\n                palette = palette,\n                onAddStoryClick = onAddStoryClick,\n                onOpenConversation = onOpenConversation,\n                onStoryClick = onStoryClick\n            )\n""",
            """            AnimatedVisibility(\n                visible = entered,\n                enter = fadeIn(tween(durationMillis = 320)) + slideInVertically(\n                    initialOffsetY = { -it / 4 },\n                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)\n                )\n            ) {\n                SearchMatchesField(\n                    value = query,\n                    onValueChange = { query = it },\n                    onVoiceSearch = startVoiceSearch,\n                    palette = palette\n                )\n            }\n\n            AnimatedVisibility(\n                visible = entered,\n                enter = fadeIn(tween(durationMillis = 380, delayMillis = 45)) + slideInVertically(\n                    initialOffsetY = { it / 5 },\n                    animationSpec = tween(\n                        durationMillis = 380,\n                        delayMillis = 45,\n                        easing = FastOutSlowInEasing\n                    )\n                )\n            ) {\n                MatchesRail(\n                    conversations = conversations,\n                    stories = stories,\n                    palette = palette,\n                    onAddStoryClick = onAddStoryClick,\n                    onOpenConversation = onOpenConversation,\n                    onStoryClick = onStoryClick\n                )\n            }\n""",
        ),
        (
            """            ConversationList(\n                conversations = filteredConversations,\n                palette = palette,\n                onOpenConversation = onOpenConversation,\n                onProfileClick = onProfileClick,\n                modifier = Modifier.weight(1f)\n            )\n""",
            """            AnimatedVisibility(\n                visible = entered,\n                modifier = Modifier.weight(1f),\n                enter = fadeIn(tween(durationMillis = 420, delayMillis = 90)) + slideInVertically(\n                    initialOffsetY = { it / 8 },\n                    animationSpec = tween(\n                        durationMillis = 420,\n                        delayMillis = 90,\n                        easing = FastOutSlowInEasing\n                    )\n                )\n            ) {\n                ConversationList(\n                    conversations = filteredConversations,\n                    palette = palette,\n                    onOpenConversation = onOpenConversation,\n                    onProfileClick = onProfileClick,\n                    modifier = Modifier.fillMaxSize()\n                )\n            }\n""",
        ),
        (
            """        Box(\n            modifier = Modifier\n                .fillMaxWidth()\n                .fillMaxHeight(.42f)\n                .background(\n                    Brush.radialGradient(\n                        colors = listOf(\n                            palette.accent.copy(alpha = if (palette.isLight) .09f else .20f),\n                            Color.Transparent\n                        ),\n                        center = Offset(120f, 90f),\n                        radius = 720f\n                    )\n                )\n        )\n        content()\n""",
            """        Box(\n            modifier = Modifier\n                .fillMaxWidth()\n                .fillMaxHeight(.42f)\n                .background(\n                    Brush.radialGradient(\n                        colors = listOf(\n                            palette.accent.copy(alpha = if (palette.isLight) .09f else .17f),\n                            Color.Transparent\n                        ),\n                        center = Offset(120f, 90f),\n                        radius = 720f\n                    )\n                )\n        )\n        Box(\n            modifier = Modifier\n                .align(Alignment.BottomEnd)\n                .fillMaxWidth()\n                .fillMaxHeight(.30f)\n                .background(\n                    Brush.radialGradient(\n                        colors = listOf(\n                            palette.accentSecondary.copy(alpha = if (palette.isLight) .05f else .09f),\n                            Color.Transparent\n                        ),\n                        center = Offset(820f, 420f),\n                        radius = 760f\n                    )\n                )\n        )\n        content()\n""",
        ),
        ("        shape = RoundedCornerShape(16.dp),\n", "        shape = RoundedCornerShape(20.dp),\n"),
        ("        shadowElevation = if (palette.isLight) 2.dp else 1.dp,\n", "        shadowElevation = if (palette.isLight) 3.dp else 5.dp,\n"),
        ("            .heightIn(min = 76.dp)\n", "            .heightIn(min = 80.dp)\n"),
        ('                Text("Pink is the default theme", color = palette.textSecondary, fontSize = 11.sp)\n', '                Text("Black is the default theme", color = palette.textSecondary, fontSize = 11.sp)\n'),
        ("            if (mode == MessageThemeMode.PINK) {\n", "            if (mode == MessageThemeMode.DARK) {\n"),
        ("                palette = messagePalette(MessageThemeMode.PINK),\n", "                palette = palette,\n"),
    ],
)

# 5) Keep the unit contract aligned with the new default.
replace_many(
    "app/src/test/java/com/example/ui/theme/MessageThemeModeTest.kt",
    [
        ("    fun missingPreferenceDefaultsToPink() {\n        assertEquals(MessageThemeMode.PINK, MessageThemeMode.fromStorage(null))\n", "    fun missingPreferenceDefaultsToDark() {\n        assertEquals(MessageThemeMode.DARK, MessageThemeMode.fromStorage(null))\n"),
        ("    fun invalidPreferenceFallsBackToPink() {\n        assertEquals(MessageThemeMode.PINK, MessageThemeMode.fromStorage(\"unknown\"))\n", "    fun invalidPreferenceFallsBackToDark() {\n        assertEquals(MessageThemeMode.DARK, MessageThemeMode.fromStorage(\"unknown\"))\n"),
    ],
)

print("Bottom navigation + black premium messaging patch complete.")
