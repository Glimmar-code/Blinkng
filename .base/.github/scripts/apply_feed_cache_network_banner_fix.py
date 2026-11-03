from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEED = ROOT / "app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt"

feed = FEED.read_text(encoding="utf-8")

connection_item = '''                        if (!isServerConnected && posts.isNotEmpty()) {
                            item(key = "connection_notice") {
                                PremiumFeedConnectionNotice(onRetry)
                            }
                        }
'''

if connection_item in feed:
    feed = feed.replace(connection_item, "", 1)
elif 'item(key = "connection_notice")' in feed:
    raise RuntimeError("Connection notice still exists, but its structure changed; refusing an unsafe patch.")

# Remove the now-unused composable so the cache/reconnect copy cannot be rendered
# accidentally from this screen again.
function_marker = '''@Composable
private fun PremiumFeedConnectionNotice(onRetry: () -> Unit) {'''
function_start = feed.find(function_marker)
if function_start >= 0:
    brace_start = feed.find("{", function_start + len("@Composable\nprivate fun PremiumFeedConnectionNotice"))
    if brace_start < 0:
        raise RuntimeError("Could not locate PremiumFeedConnectionNotice body.")

    depth = 0
    function_end = None
    for index in range(brace_start, len(feed)):
        char = feed[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                function_end = index + 1
                break

    if function_end is None:
        raise RuntimeError("Could not find the end of PremiumFeedConnectionNotice.")

    while function_end < len(feed) and feed[function_end] == "\n":
        function_end += 1
    feed = feed[:function_start] + feed[function_end:]

feed = feed.replace("import androidx.compose.material.icons.filled.WifiOff\n", "", 1)

# Safety checks: preserve the offline-first/cache system while removing only the
# noisy in-feed reconnect card.
required_markers = [
    "NetworkMonitor(context)",
    "offlineEmptyConfirmed",
    "cache_wait:$it",
]
missing = [marker for marker in required_markers if marker not in feed]
if missing:
    raise RuntimeError(f"Offline/cache behavior unexpectedly missing after patch: {missing}")

if 'item(key = "connection_notice")' in feed:
    raise RuntimeError("Visible connection notice was not removed.")
if "Showing available cached posts while Blink reconnects." in feed:
    raise RuntimeError("Cached-post reconnect copy was not removed.")

FEED.write_text(feed, encoding="utf-8")
print("Removed the in-feed cached/reconnecting notice while preserving offline cache behavior.")
