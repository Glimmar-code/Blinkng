from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"

text = VM.read_text(encoding="utf-8")

# The app source now owns session restoration and onboarding routing directly.
# This workflow must never replace restoreSupabaseSession() with a stale copy.
required = [
    "private fun destinationForProfile(profile: UserProfile)",
    "KEY_ONBOARDING_COMPLETED",
    "onboardingCompleted = onboardingCompleted",
    "destination = destinationForProfile(profile)",
]

missing = [token for token in required if token not in text]
if missing:
    raise SystemExit(
        "Session/onboarding invariants are missing; refusing to rewrite BlinkViewModel: "
        + ", ".join(missing)
    )

print("Session persistence and onboarding routing verified; no source rewrite needed.")
