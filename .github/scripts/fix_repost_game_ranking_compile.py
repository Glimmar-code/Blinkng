from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count} in {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


post_card = ROOT / "app/src/main/java/com/example/ui/components/PostCard.kt"
replace_once(
    post_card,
    "    onRepost: () -> Unit,\n",
    "    onRepost: () -> Unit = {},\n",
    "default repost callback",
)

view_model = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
replace_once(
    view_model,
    """    fun recordGameResult(gameType: String, score: Int) {\n        runConnectAction(\"Game result synced.\") {\n            connectHubRepository.recordGameSession(gameType, score)\n            val live = runCatching { supabaseService.fetchGameLeaderboard() }\n                .getOrDefault(_uiState.value.gameLeaderboardUsers)\n            _uiState.value = _uiState.value.copy(gameLeaderboardUsers = live)\n        }\n    }\n""",
    """    fun recordGameResult(gameType: String, score: Int) {\n        runConnectAction(\"Game result synced.\") {\n            val synced = connectHubRepository.recordGameSession(gameType, score)\n            if (synced) {\n                val live = runCatching { supabaseService.fetchGameLeaderboard() }\n                    .getOrDefault(_uiState.value.gameLeaderboardUsers)\n                _uiState.value = _uiState.value.copy(gameLeaderboardUsers = live)\n            }\n            synced\n        }\n    }\n""",
    "game result boolean return",
)

print("Compile wiring fixes applied.")
