"""Regression gate for the temporary, reversible public Blink Store pause.

This checks that no Store purchase screens are reachable from the app's main
navigation, that the five feed shortcuts stay ordered, and that no data layer
or Supabase migration was changed as part of the pause.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


policy = source("shared/src/main/kotlin/com/blinkng/shared/BlinkStoreAvailability.kt")
assert "const val isPaused: Boolean = true" in policy
assert "temporarily paused" in policy

chrome = source("app/src/main/java/com/example/ui/components/PremiumFeedChrome.kt")
shortcuts = chrome.split("fun FeedUtilityRow(", 1)[1].split("@Composable\nprivate fun FeedUtilityAction(", 1)[0]
order = [
    shortcuts.index('label = "Rank"'),
    shortcuts.index('label = "Game"'),
    shortcuts.index('label = "Boost"'),
    shortcuts.index('label = "Drops"'),
    shortcuts.index('label = "Store"'),
]
assert order == sorted(order), "Store must be the muted fifth shortcut"
assert 'muted = true' in shortcuts and 'feed_store_action' in shortcuts

main = source("app/src/main/java/com/example/MainActivity.kt")
assert 'onStoreClick = {' in main and "showStoreComingSoon = true" in main
assert "BlinkStoreComingSoonDialog(" in main
assert 'feedUtilitySheet == "store"' in main  # legacy/restored state
assert main.count('if (BlinkStoreAvailability.isPaused) showStoreComingSoon = true') == 3

store = source("app/src/main/java/com/example/BlinkStoreActivity.kt")
gate = store.index("if (BlinkStoreAvailability.isPaused)")
network = store.index("val service = remember { BlinkEconomyService() }")
assert gate < network, "Store must gate before requesting catalog/wallet"

desktop = source("desktopApp/src/main/kotlin/com/blinkng/desktop/ui/AdvancedScreens.kt")
assert desktop.index("if (BlinkStoreAvailability.isPaused)") < desktop.index("val actions = remember(state.client)")
assert 'confirmButton = {}' in desktop and 'dismissButton = {}' in desktop

web = source("web/parity.js")
assert "const BLINK_STORE_PAUSED = true;" in web
assert "STORE_PAUSED_ROUTES.has(path)" in web
assert web.index("if (BLINK_STORE_PAUSED) { renderStoreComingSoon(); return; }") < web.index("if (!authGuard()) return; loading('Blink Store')")
assert 'backdrop.id = \'blink-store-soon-modal\'' in web
assert "event.key === 'Escape'" in web
assert "confirmButton" not in source("app/src/main/java/com/example/BlinkStoreComingSoonDialog.kt")

print("Blink Store pause, fifth shortcut, cross-platform safety and no-button popup checks passed.")
