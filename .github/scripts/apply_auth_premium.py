from pathlib import Path

AUTH = Path("app/src/main/java/com/example/ui/screens/AuthScreens.kt")
MAIN = Path("app/src/main/java/com/example/MainActivity.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Missing expected block for {label}")
    return text.replace(old, new, 1)


def patch_auth() -> None:
    text = AUTH.read_text(encoding="utf-8")

    sign_in_marker = "// ================================================================\n// SIGN IN\n// ================================================================\n"
    sign_up_marker = "// ================================================================\n// SIGN UP\n// ================================================================\n"
    profile_marker = "// ================================================================\n// PROFILE SETUP\n// ================================================================\n"

    before, rest = text.split(sign_in_marker, 1)
    sign_in, rest = rest.split(sign_up_marker, 1)
    sign_up, after = rest.split(profile_marker, 1)

    sign_in = replace_once(
        sign_in,
        """    val coroutineScope =\n        rememberCoroutineScope()\n\n    Box(\n        modifier = Modifier\n            .fillMaxSize()\n            .background(DarkBackground)\n    ) {\n\n        LazyColumn(\n            modifier = Modifier\n                .fillMaxSize()\n                .statusBarsPadding()\n""",
        """    val coroutineScope =\n        rememberCoroutineScope()\n\n    var entranceReady by remember { mutableStateOf(false) }\n    val entranceAlpha by animateFloatAsState(\n        targetValue = if (entranceReady) 1f else 0f,\n        animationSpec = tween(420, easing = FastOutSlowInEasing),\n        label = \"signin_alpha\"\n    )\n    val entranceOffset by animateDpAsState(\n        targetValue = if (entranceReady) 0.dp else 18.dp,\n        animationSpec = tween(520, easing = FastOutSlowInEasing),\n        label = \"signin_offset\"\n    )\n\n    LaunchedEffect(Unit) { entranceReady = true }\n\n    Box(\n        modifier = Modifier\n            .fillMaxSize()\n            .background(blinkBackgroundBrush(isDark = true))\n    ) {\n\n        DecorativeAuthBackground()\n\n        LazyColumn(\n            modifier = Modifier\n                .fillMaxSize()\n                .alpha(entranceAlpha)\n                .offset(y = entranceOffset)\n                .statusBarsPadding()\n""",
        "signin entrance and background",
    )

    sign_in = replace_once(
        sign_in,
        """                    colors = ButtonDefaults.buttonColors(\n                        containerColor = BlinkPink\n                    ),\n""",
        """                    colors = ButtonDefaults.buttonColors(\n                        containerColor = BlinkCream,\n                        contentColor = BlinkBlack,\n                        disabledContainerColor = DarkSurfaceElevated,\n                        disabledContentColor = DarkTextMuted\n                    ),\n""",
        "signin submit colors",
    )

    sign_in = sign_in.replace(
        """                            colors = CheckboxDefaults.colors(\n                                checkedColor = BlinkPink,\n                                uncheckedColor = DarkTextSecondary\n                            )\n""",
        """                            colors = CheckboxDefaults.colors(\n                                checkedColor = BlinkGold,\n                                checkmarkColor = BlinkBlack,\n                                uncheckedColor = DarkTextMuted\n                            )\n""",
        1,
    )
    sign_in = sign_in.replace('color = BlinkLavender,', 'color = BlinkGoldSoft,', 1)
    sign_in = sign_in.replace('color = BlinkPink,\n                            fontWeight = FontWeight.Bold', 'color = BlinkGoldSoft,\n                            fontWeight = FontWeight.Bold', 1)
    sign_in = sign_in.replace('color = Color.White,\n                            strokeWidth = 2.dp', 'color = BlinkBlack,\n                            strokeWidth = 2.dp', 1)

    sign_up = replace_once(
        sign_up,
        """    val coroutineScope = rememberCoroutineScope()\n\n    val isFormReady =\n""",
        """    val coroutineScope = rememberCoroutineScope()\n\n    var entranceReady by remember { mutableStateOf(false) }\n    val entranceAlpha by animateFloatAsState(\n        targetValue = if (entranceReady) 1f else 0f,\n        animationSpec = tween(420, easing = FastOutSlowInEasing),\n        label = \"signup_alpha\"\n    )\n    val entranceOffset by animateDpAsState(\n        targetValue = if (entranceReady) 0.dp else 18.dp,\n        animationSpec = tween(520, easing = FastOutSlowInEasing),\n        label = \"signup_offset\"\n    )\n\n    LaunchedEffect(Unit) { entranceReady = true }\n\n    val isFormReady =\n""",
        "signup entrance state",
    )

    sign_up = replace_once(
        sign_up,
        """    Box(\n        modifier = Modifier\n            .fillMaxSize()\n            .background(DarkBackground)\n    ) {\n\n        LazyColumn(\n            modifier = Modifier\n                .fillMaxSize()\n                .statusBarsPadding()\n""",
        """    Box(\n        modifier = Modifier\n            .fillMaxSize()\n            .background(blinkBackgroundBrush(isDark = true))\n    ) {\n\n        DecorativeAuthBackground()\n\n        LazyColumn(\n            modifier = Modifier\n                .fillMaxSize()\n                .alpha(entranceAlpha)\n                .offset(y = entranceOffset)\n                .statusBarsPadding()\n""",
        "signup entrance and background",
    )

    sign_up = replace_once(
        sign_up,
        """                    colors = ButtonDefaults.buttonColors(\n                        containerColor = BlinkPink,\n                        disabledContainerColor = DarkSurface\n                    ),\n""",
        """                    colors = ButtonDefaults.buttonColors(\n                        containerColor = BlinkCream,\n                        contentColor = BlinkBlack,\n                        disabledContainerColor = DarkSurfaceElevated,\n                        disabledContentColor = DarkTextMuted\n                    ),\n""",
        "signup submit colors",
    )

    sign_up = sign_up.replace(
        """                        colors = CheckboxDefaults.colors(\n                            checkedColor = BlinkPink\n                        )\n""",
        """                        colors = CheckboxDefaults.colors(\n                            checkedColor = BlinkGold,\n                            checkmarkColor = BlinkBlack,\n                            uncheckedColor = DarkTextMuted\n                        )\n""",
        1,
    )
    sign_up = sign_up.replace('color = BlinkPink,\n                            fontWeight = FontWeight.Bold', 'color = BlinkGoldSoft,\n                            fontWeight = FontWeight.Bold', 1)

    text = before + sign_in_marker + sign_in + sign_up_marker + sign_up + profile_marker + after

    # Google auth button: keep Google's clean surface but integrate it with Blink's cream/gold palette.
    text = replace_once(
        text,
        """        color = Color.White,\n        shadowElevation = 4.dp,\n        modifier = modifier\n""",
        """        color = BlinkCreamBright,\n        border = BorderStroke(1.dp, BlinkGold.copy(alpha = 0.30f)),\n        shadowElevation = 0.dp,\n        modifier = modifier\n""",
        "google auth surface",
    )

    # Premium field typography and contrast.
    text = text.replace(
        """        label = {\n            Text(label)\n        },\n""",
        """        label = {\n            Text(\n                label,\n                style = MaterialTheme.typography.bodySmall,\n                fontWeight = FontWeight.Medium\n            )\n        },\n""",
        1,
    )
    text = text.replace(
        """        singleLine = true,\n        shape = RoundedCornerShape(16.dp),\n        colors = OutlinedTextFieldDefaults.colors(\n            focusedBorderColor = BlinkPink,\n            unfocusedBorderColor = DarkBorder,\n            focusedTextColor = Color.White,\n            unfocusedTextColor = Color.White,\n            focusedLabelColor = BlinkPink,\n            unfocusedLabelColor = DarkTextSecondary,\n            focusedContainerColor = DarkSurface,\n            unfocusedContainerColor = DarkSurface\n        ),\n""",
        """        singleLine = true,\n        textStyle = MaterialTheme.typography.bodyLarge.copy(\n            fontWeight = FontWeight.Medium,\n            letterSpacing = 0.sp\n        ),\n        shape = RoundedCornerShape(18.dp),\n        colors = OutlinedTextFieldDefaults.colors(\n            focusedBorderColor = BlinkGoldSoft,\n            unfocusedBorderColor = DarkBorderSoft,\n            focusedTextColor = DarkTextPrimary,\n            unfocusedTextColor = DarkTextPrimary,\n            cursorColor = BlinkGoldSoft,\n            focusedLabelColor = BlinkGoldSoft,\n            unfocusedLabelColor = DarkTextSecondary,\n            focusedLeadingIconColor = BlinkGoldSoft,\n            unfocusedLeadingIconColor = DarkTextSecondary,\n            focusedContainerColor = DarkSurfaceElevated,\n            unfocusedContainerColor = DarkSurface\n        ),\n""",
        1,
    )

    # Password field gets matching typography/contrast.
    text = text.replace(
        """        label = {\n            Text(\"Password\")\n        },\n""",
        """        label = {\n            Text(\n                \"Password\",\n                style = MaterialTheme.typography.bodySmall,\n                fontWeight = FontWeight.Medium\n            )\n        },\n""",
        1,
    )
    text = text.replace(
        """        singleLine = true,\n        shape = RoundedCornerShape(16.dp),\n        colors = OutlinedTextFieldDefaults.colors(\n            focusedBorderColor = BlinkPink,\n            unfocusedBorderColor = DarkBorder,\n            focusedTextColor = Color.White,\n            unfocusedTextColor = Color.White,\n            focusedLabelColor = BlinkPink,\n            unfocusedLabelColor = DarkTextSecondary,\n            focusedContainerColor = DarkSurface,\n            unfocusedContainerColor = DarkSurface\n        ),\n""",
        """        singleLine = true,\n        textStyle = MaterialTheme.typography.bodyLarge.copy(\n            fontWeight = FontWeight.Medium,\n            letterSpacing = 0.sp\n        ),\n        shape = RoundedCornerShape(18.dp),\n        colors = OutlinedTextFieldDefaults.colors(\n            focusedBorderColor = BlinkGoldSoft,\n            unfocusedBorderColor = DarkBorderSoft,\n            focusedTextColor = DarkTextPrimary,\n            unfocusedTextColor = DarkTextPrimary,\n            cursorColor = BlinkGoldSoft,\n            focusedLabelColor = BlinkGoldSoft,\n            unfocusedLabelColor = DarkTextSecondary,\n            focusedLeadingIconColor = BlinkGoldSoft,\n            unfocusedLeadingIconColor = DarkTextSecondary,\n            focusedContainerColor = DarkSurfaceElevated,\n            unfocusedContainerColor = DarkSurface\n        ),\n""",
        1,
    )

    # Top bar + hero typography. DarkTextPrimary is cream-tinted and reads better on the black background.
    text = text.replace(
        """                title,\n                color = Color.White,\n                fontWeight = FontWeight.Bold,\n                fontSize = 15.sp\n""",
        """                title,\n                color = DarkTextPrimary,\n                style = MaterialTheme.typography.titleMedium,\n                fontWeight = FontWeight.ExtraBold,\n                letterSpacing = (-0.2).sp\n""",
        1,
    )
    text = text.replace(
        """                title,\n                fontWeight = FontWeight.Black,\n                fontSize = 22.sp,\n                color = Color.White\n""",
        """                title,\n                style = MaterialTheme.typography.headlineSmall,\n                fontWeight = FontWeight.ExtraBold,\n                letterSpacing = (-0.45).sp,\n                color = DarkTextPrimary\n""",
        1,
    )

    # Smooth password-strength state instead of a hard jump.
    text = replace_once(
        text,
        """    val progress = score / 4f\n\n    val color =\n""",
        """    val progress = score / 4f\n    val animatedProgress by animateFloatAsState(\n        targetValue = progress,\n        animationSpec = tween(320, easing = FastOutSlowInEasing),\n        label = \"password_strength_progress\"\n    )\n\n    val color =\n""",
        "password strength animation",
    )
    text = text.replace("""            progress = {\n                progress\n            },\n""", """            progress = {\n                animatedProgress\n            },\n""", 1)

    # Faculty chips: add a tiny selected-state scale and correct cream-on-white contrast.
    text = replace_once(
        text,
        """private fun PremiumChoiceChip(\n    text: String,\n    selected: Boolean,\n    onClick: () -> Unit\n) {\n\n    Surface(\n""",
        """private fun PremiumChoiceChip(\n    text: String,\n    selected: Boolean,\n    onClick: () -> Unit\n) {\n\n    val chipScale by animateFloatAsState(\n        targetValue = if (selected) 1.035f else 1f,\n        animationSpec = tween(180, easing = FastOutSlowInEasing),\n        label = \"auth_choice_chip_scale\"\n    )\n\n    Surface(\n""",
        "choice chip animation",
    )
    text = text.replace(
        """        modifier = Modifier.clickable {\n            onClick()\n        }\n""",
        """        modifier = Modifier\n            .scale(chipScale)\n            .clickable { onClick() }\n""",
        1,
    )
    text = text.replace(
        """                    tint = Color.White,\n                    modifier = Modifier.size(13.dp)\n""",
        """                    tint = BlinkBlack,\n                    modifier = Modifier.size(13.dp)\n""",
        1,
    )
    text = text.replace(
        """                    if (selected)\n                        Color.White\n                    else\n                        DarkTextSecondary,\n""",
        """                    if (selected)\n                        BlinkBlack\n                    else\n                        DarkTextSecondary,\n""",
        1,
    )

    # Onboarding auth buttons share the same palette; keep primary text readable on cream.
    text = text.replace(
        """                tint = Color.White,\n                modifier = Modifier.size(17.dp)\n""",
        """                tint = if (primary) BlinkBlack else DarkTextPrimary,\n                modifier = Modifier.size(17.dp)\n""",
        1,
    )
    text = text.replace(
        """                color = Color.White,\n                fontWeight = FontWeight.Bold,\n                fontSize = 13.5.sp\n""",
        """                color = if (primary) BlinkBlack else DarkTextPrimary,\n                style = MaterialTheme.typography.labelLarge,\n                fontWeight = FontWeight.Bold\n""",
        1,
    )

    # Auth messages should use the theme's warm primary text instead of stark white.
    text = text.replace("color = Color.White,\n                fontSize = 11.sp,", "color = DarkTextPrimary,\n                fontSize = 11.sp,", 1)

    AUTH.write_text(text, encoding="utf-8")


def patch_main() -> None:
    text = MAIN.read_text(encoding="utf-8")
    text = replace_once(
        text,
        """                            transitionSpec = {\n                                fadeIn(animationSpec = tween(300)) togetherWith\n                                        fadeOut(animationSpec = tween(300))\n                            },\n""",
        """                            transitionSpec = {\n                                (fadeIn(\n                                    animationSpec = tween(340, easing = FastOutSlowInEasing)\n                                ) + slideInVertically(\n                                    animationSpec = tween(420, easing = FastOutSlowInEasing),\n                                    initialOffsetY = { it / 18 }\n                                ) + scaleIn(\n                                    initialScale = 0.985f,\n                                    animationSpec = tween(420, easing = FastOutSlowInEasing)\n                                )) togetherWith\n                                        (fadeOut(animationSpec = tween(180)) +\n                                                scaleOut(\n                                                    targetScale = 0.995f,\n                                                    animationSpec = tween(180)\n                                                ))\n                            },\n""",
        "app navigation transition",
    )
    MAIN.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_auth()
    patch_main()
    print("Applied premium auth theme, typography, contrast and motion updates.")
