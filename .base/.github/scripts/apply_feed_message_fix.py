from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
SERVICE = ROOT / "app/src/main/java/com/example/data/supabase/SupabaseService.kt"

vm = VM.read_text()
service = SERVICE.read_text()

required_vm_rules = [
    "!it.isReel && it.videoUrl.isNullOrBlank()",
    "it.isReel || !it.videoUrl.isNullOrBlank()",
    "supabaseService.fetchFeedPosts()",
]
required_service_rules = [
    "val effectiveIsReel = !cleanVideoUrl.isNullOrBlank()",
    "val parsedIsReel = !parsedVideoUrl.isNullOrBlank()",
    "/auth/v1/health",
]

missing = [rule for rule in required_vm_rules if rule not in vm]
missing += [rule for rule in required_service_rules if rule not in service]

if missing:
    raise SystemExit(
        "Feed/message safety check failed; required live-feed rules are missing: "
        + ", ".join(missing)
    )

print("Feed/message safety check passed; no source rewrite required.")
