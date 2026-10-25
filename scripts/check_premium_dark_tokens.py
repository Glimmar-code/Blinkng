#!/usr/bin/env python3
"""Guard Auth-derived dark surfaces against legacy per-screen palette regressions.

No dependencies, no backend access. Intentionally excludes media scrims, user-selected
post colors, Google branding, VIP cosmetics and light-mode-specific artwork; those
have product meanings and must not be blindly converted into theme surfaces.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(path: str, *needles: str) -> None:
    content = source(path)
    for needle in needles:
        if needle not in content:
            raise AssertionError(f"{path}: missing dark-theme contract: {needle}")


def forbid(path: str, *needles: str) -> None:
    content = source(path)
    for needle in needles:
        if needle in content:
            raise AssertionError(f"{path}: legacy hardcoded dark UI color: {needle}")


def main() -> None:
    shared = "shared/src/main/kotlin/com/blinkng/shared/BlinkDesignSystem.kt"
    require(shared, "object PremiumDark", "const val Background: Long = 0xFF000000L",
            "const val CardOverlayAlpha = 0.055f", "const val SubtleBorderAlpha = 0.08f")
    require("app/src/main/java/com/example/ui/theme/BlinkDarkTheme.kt",
            "surfaceContainerLowest = DarkBackground",
            "surfaceContainerHigh = DarkSurfaceHighest")
    require("desktopApp/src/main/kotlin/com/blinkng/desktop/ui/BlinkDesktopTheme.kt",
            "surfaceContainerLowest = Color(BlinkDesignTokens.Dark.Background)")
    require("desktopApp/src/main/kotlin/com/blinkng/desktop/DesktopShell.kt",
            "BlinkDesktopTheme(appearance = appState.settings?.theme)")
    forbid("desktopApp/src/main/kotlin/com/blinkng/desktop/DesktopShell.kt",
           "private val BlinkDarkColors", "private val BlinkBackground = Color(0x")
    require("app/src/main/java/com/example/ui/components/BlinkPremiumComponents.kt",
            "PremiumDark.CardOverlayAlpha", "PremiumDark.SubtleBorderAlpha",
            "PremiumDark.ButtonCornerRadius")
    require("app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt",
            "if (isDark) MaterialTheme.colorScheme.background")
    forbid("app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt",
           "if (isDark) Color(0xFF08090B)")
    require("app/src/main/java/com/example/ui/components/PostCard.kt",
            "if (isDark) FeedCardSurface else Color.White")
    forbid("app/src/main/java/com/example/ui/components/PostCard.kt",
           "if (isDark) Color(0xFF17191D)")
    require("app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt",
            "Color(BlinkDesignTokens.Dark.SurfaceHighest)")
    forbid("app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt",
           "Color(0xFF343434)", "Color(0xFF202020)")
    require("app/src/main/java/com/example/ui/components/BlinkAiSheet.kt",
            "val authDark = MaterialTheme.colorScheme.background == Color.Black",
            "BlinkDesignTokens.Brand.PrimaryBright")
    require("desktopApp/src/main/kotlin/com/blinkng/desktop/ui/DesktopBlinkAiHome.kt",
            "val authDark = MaterialTheme.colorScheme.background == Color.Black")
    require("app/src/main/java/com/example/AdminControlCenterActivity.kt",
            "MaterialTheme.colorScheme.surfaceContainer")
    forbid("app/src/main/java/com/example/AdminControlCenterActivity.kt",
           "Color(0xFF101014)", "Color(0xFF171521)")
    require("app/src/main/java/com/example/ui/theme/MessageTheme.kt",
            "Color(BlinkDesignTokens.Dark.Background)", "Color(BlinkDesignTokens.Brand.Primary)")
    print("PASS: BLINK Auth-inspired dark surface/brand contracts hold across Android and Windows.")


if __name__ == "__main__":
    main()
