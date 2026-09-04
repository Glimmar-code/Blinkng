from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
auth_path = ROOT / "app/src/main/java/com/example/ui/screens/AuthScreens.kt"
main_path = ROOT / "app/src/main/java/com/example/MainActivity.kt"

auth = auth_path.read_text(encoding="utf-8")
main = main_path.read_text(encoding="utf-8")

new_splash = r'''@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    var started by remember { mutableStateOf(false) }

    val bScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.78f,
        animationSpec = tween(
            durationMillis = 260,
            easing = FastOutSlowInEasing
        ),
        label = "splash_b_scale"
    )

    val bAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = 180,
            easing = FastOutSlowInEasing
        ),
        label = "splash_b_alpha"
    )

    LaunchedEffect(Unit) {
        started = true
        delay(300)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "B",
            color = BlinkCream,
            fontSize = 74.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-2).sp,
            modifier = Modifier
                .alpha(bAlpha)
                .scale(bScale)
        )
    }
}
'''

pattern = re.compile(
    r'@Composable\nfun SplashScreen\(.*?\n}\n\n// ================================================================\n// ONBOARDING',
    re.S,
)
match = pattern.search(auth)
if not match:
    raise SystemExit("SplashScreen block not found")
auth = auth[:match.start()] + new_splash + "\n// ================================================================\n// ONBOARDING" + auth[match.end():]

auth_path.write_text(auth, encoding="utf-8")

old_transition = '''                            transitionSpec = {
                                (fadeIn(
                                    animationSpec = tween(340, easing = FastOutSlowInEasing)
                                ) + slideInVertically(
                                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                                    initialOffsetY = { it / 18 }
                                ) + scaleIn(
                                    initialScale = 0.985f,
                                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                                )) togetherWith
                                        (fadeOut(animationSpec = tween(180)) +
                                                scaleOut(
                                                    targetScale = 0.995f,
                                                    animationSpec = tween(180)
                                                ))
                            },'''

new_transition = '''                            transitionSpec = {
                                if (initialState == AppDestination.SPLASH || targetState == AppDestination.SPLASH) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (fadeIn(
                                        animationSpec = tween(340, easing = FastOutSlowInEasing)
                                    ) + slideInVertically(
                                        animationSpec = tween(420, easing = FastOutSlowInEasing),
                                        initialOffsetY = { it / 18 }
                                    ) + scaleIn(
                                        initialScale = 0.985f,
                                        animationSpec = tween(420, easing = FastOutSlowInEasing)
                                    )) togetherWith
                                            (fadeOut(animationSpec = tween(180)) +
                                                    scaleOut(
                                                        targetScale = 0.995f,
                                                        animationSpec = tween(180)
                                                    ))
                                }
                            },'''

if old_transition not in main:
    raise SystemExit("App navigation transition block not found")
main = main.replace(old_transition, new_transition, 1)
main_path.write_text(main, encoding="utf-8")
