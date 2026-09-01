from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

# 1) Make the Kotlin feed parser trust a video only when the row is explicitly a reel.
service = ROOT / "app/src/main/java/com/example/data/supabase/SupabaseService.kt"
s = service.read_text()
old = '''        val isVerified = obj.optBoolean("is_verified", false) || obj.optString("verification_badge", "").equals("BLUE", ignoreCase = true) || obj.optString("verification_badge", "").equals("GOLD", ignoreCase = true)'''
new = '''        val parsedVideoUrl = obj.optString("video_url", "").takeIf { it.isNotBlank() }\n        val parsedType = obj.optString("type", "").lowercase(Locale.US)\n        val parsedIsReel = parsedType == "reel" && !parsedVideoUrl.isNullOrBlank() && obj.optBoolean("is_reel", true)\n\n        val isVerified = obj.optBoolean("is_verified", false) || obj.optString("verification_badge", "").equals("BLUE", ignoreCase = true) || obj.optString("verification_badge", "").equals("GOLD", ignoreCase = true)'''
if old in s and "val parsedVideoUrl" not in s:
    s = s.replace(old, new, 1)
s = s.replace('''            isReel = obj.optBoolean("is_reel", false),\n            videoDuration = obj.optString("video_duration", "0:00"),\n            videoUrl = obj.optString("video_url", null),''', '''            isReel = parsedIsReel,\n            videoDuration = obj.optString("video_duration", "0:00"),\n            videoUrl = parsedVideoUrl,''', 1)
service.write_text(s)

# 2) The current chat call buttons are visually present but have empty click handlers.
# Wire them to a deterministic Jitsi room so audio/video calls actually launch.
messages = ROOT / "app/src/main/java/com/example/ui/screens/MessagesScreen.kt"
m = messages.read_text()
if 'import android.content.Intent' not in m:
    m = 'import android.content.Intent\nimport android.net.Uri\n' + m
needle = '''private fun ChatTopBar(\n    convo: ChatConversation,'''
if needle in m and 'val callContext = LocalContext.current' not in m:
    m = m.replace(needle, needle, 1)
    marker = ''') {\n\n    TopAppBar('''
    replacement = ''') {\n\n    val callContext = LocalContext.current\n    val callRoom = "https://meet.jit.si/Blink-${convo.id}"\n\n    TopAppBar('''
    # The marker occurs in ChatTopBar's parameter close; use the first occurrence after the function.
    pos = m.find(needle)
    end = m.find(marker, pos)
    if end != -1:
        m = m[:end] + replacement + m[end + len(marker):]
        m = m.replace('''IconButton(\n                onClick = {}\n            ) {\n\n                Icon(\n                    Icons.Default.Phone,''', '''IconButton(\n                onClick = {\n                    callContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$callRoom#config.startWithVideoMuted=true")))\n                }\n            ) {\n\n                Icon(\n                    Icons.Default.Phone,''', 1)
        m = m.replace('''IconButton(\n                onClick = {}\n            ) {\n\n                Icon(\n                    Icons.Default.Videocam,''', '''IconButton(\n                onClick = {\n                    callContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(callRoom)))\n                }\n            ) {\n\n                Icon(\n                    Icons.Default.Videocam,''', 1)
messages.write_text(m)

# 3) Make the Google callback surface OAuth errors instead of silently returning to the app.
callback = ROOT / "app/src/main/java/com/example/auth/GoogleAuthCallbackActivity.kt"
c = callback.read_text()
old_error = '''        val accessToken = params["access_token"]\n        val refreshToken = params["refresh_token"]\n        if (!accessToken.isNullOrBlank()) SupabaseService.saveSession(accessToken, refreshToken)'''
new_error = '''        val accessToken = params["access_token"]\n        val refreshToken = params["refresh_token"]\n        val errorDescription = params["error_description"] ?: params["error"]\n        if (!errorDescription.isNullOrBlank()) {\n            android.util.Log.e("GoogleAuthCallback", "OAuth failed: $errorDescription")\n            returnToMain()\n            return\n        }\n        if (!accessToken.isNullOrBlank()) SupabaseService.saveSession(accessToken, refreshToken)'''
if old_error in c:
    c = c.replace(old_error, new_error, 1)
callback.write_text(c)
