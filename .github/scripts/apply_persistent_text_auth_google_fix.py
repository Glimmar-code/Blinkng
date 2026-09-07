from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def ensure_import(text: str, import_line: str) -> str:
    if import_line in text:
        return text
    package_end = text.find("\n\n")
    if package_end < 0:
        raise RuntimeError(f"Could not locate package header for {import_line}")
    return text[: package_end + 2] + import_line + "\n" + text[package_end + 2 :]


# -----------------------------------------------------------------------------
# Encrypted, account-scoped persistent text state used by Compose input fields.
# -----------------------------------------------------------------------------
persistent_state_path = Path("app/src/main/java/com/example/data/local/PersistentTextDraftStore.kt")
persistent_state_path.parent.mkdir(parents=True, exist_ok=True)
persistent_state_path.write_text(r'''package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray

/**
 * Durable per-account drafts for text the user is actively typing.
 *
 * Values are encrypted with an AndroidKeyStore AES key before they are written to
 * private SharedPreferences. Assigning an empty value removes the stored draft, so
 * existing send/submit handlers that clear their field also clear the durable copy.
 */
object PersistentTextDraftStore {
    private const val PREFS = "blink_persistent_text_drafts_v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "blink_persistent_text_drafts_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun accountScope(context: Context): String {
        fun from(name: String): String {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            return prefs.getString("email", "").orEmpty().trim().lowercase().ifBlank {
                prefs.getString("username", "").orEmpty().trim().removePrefix("@").lowercase()
            }
        }
        return from("blink_auth_prefs").ifBlank { from("blink_user_session") }.ifBlank { "guest" }
    }

    internal fun storageKey(context: Context, key: String, scope: String): String {
        val raw = "${accountScope(context)}|${scope.trim()}|${key.trim()}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
                cachedKey = it
                return it
            }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey().also { cachedKey = it }
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12) { "Invalid encrypted draft" }
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    internal fun readStorageKey(context: Context, storageKey: String): String? = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(storageKey, null)
            ?: return null
        decrypt(raw)
    } catch (_: Exception) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(storageKey).apply()
        null
    }

    internal fun writeStorageKey(context: Context, storageKey: String, value: String) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (value.isEmpty()) {
            editor.remove(storageKey).apply()
        } else {
            runCatching { encrypt(value) }
                .onSuccess { encrypted -> editor.putString(storageKey, encrypted).apply() }
        }
    }
}

@Composable
fun rememberPersistentTextState(
    key: String,
    initialValue: String = "",
    scope: String = ""
): MutableState<String> {
    val context = LocalContext.current.applicationContext
    val storageKey = remember(key, scope) {
        PersistentTextDraftStore.storageKey(context, key, scope)
    }
    val backing = remember(storageKey) {
        mutableStateOf(PersistentTextDraftStore.readStorageKey(context, storageKey) ?: initialValue)
    }
    return remember(storageKey, backing) {
        object : MutableState<String> {
            override var value: String
                get() = backing.value
                set(newValue) {
                    backing.value = newValue
                    PersistentTextDraftStore.writeStorageKey(context, storageKey, newValue)
                }
        }
    }
}

@Composable
fun rememberPersistentStringListState(
    key: String,
    initialValue: List<String>,
    scope: String = ""
): MutableState<List<String>> {
    val context = LocalContext.current.applicationContext
    val storageKey = remember(key, scope) {
        PersistentTextDraftStore.storageKey(context, key, scope)
    }
    fun decode(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) add(array.optString(i))
            }
        }.getOrNull()
    }
    fun encode(values: List<String>): String {
        val array = JSONArray()
        values.forEach(array::put)
        return array.toString()
    }
    val backing = remember(storageKey) {
        mutableStateOf(decode(PersistentTextDraftStore.readStorageKey(context, storageKey)) ?: initialValue)
    }
    return remember(storageKey, backing) {
        object : MutableState<List<String>> {
            override var value: List<String>
                get() = backing.value
                set(newValue) {
                    backing.value = newValue
                    val hasTypedContent = newValue.any { it.isNotBlank() }
                    PersistentTextDraftStore.writeStorageKey(
                        context,
                        storageKey,
                        if (hasTypedContent) encode(newValue) else ""
                    )
                }
        }
    }
}
''')


# -----------------------------------------------------------------------------
# Login credentials: encrypted at rest and controlled by the Remember checkbox.
# -----------------------------------------------------------------------------
remember_login_path = Path("app/src/main/java/com/example/auth/RememberedLoginStore.kt")
remember_login_path.write_text(r'''package com.example.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Secure local storage for the login identifier/password when the user opts in. */
object RememberedLoginStore {
    private const val PREFS = "blink_remembered_login_v1"
    private const val VALUE = "credentials"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "blink_remembered_login_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    data class Login(val identifier: String, val password: String)

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
                cachedKey = it
                return it
            }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey().also { cachedKey = it }
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(
            cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > 12) { "Invalid remembered login" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, combined.copyOfRange(0, 12))
        )
        return String(cipher.doFinal(combined.copyOfRange(12, combined.size)), StandardCharsets.UTF_8)
    }

    fun save(context: Context, identifier: String, password: String) {
        if (identifier.isBlank() && password.isBlank()) return
        val raw = JSONObject()
            .put("identifier", identifier)
            .put("password", password)
            .toString()
        runCatching { encrypt(raw) }
            .onSuccess { encrypted ->
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(VALUE, encrypted)
                    .apply()
            }
    }

    fun load(context: Context): Login? = try {
        val encrypted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(VALUE, null)
            ?: return null
        val obj = JSONObject(decrypt(encrypted))
        Login(obj.optString("identifier"), obj.optString("password"))
    } catch (_: Exception) {
        clear(context)
        null
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(VALUE)
            .apply()
    }
}
''')


# -----------------------------------------------------------------------------
# Google native ID-token nonce contract: hashed to Google, raw to Supabase.
# -----------------------------------------------------------------------------
google_nonce_path = Path("app/src/main/java/com/example/auth/GoogleNonce.kt")
google_nonce_path.write_text(r'''package com.example.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

internal object GoogleNonce {
    fun generate(byteLength: Int = 32): String {
        require(byteLength > 0) { "Nonce length must be positive." }
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    fun sha256Hex(rawNonce: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
''')

google_activity_path = Path("app/src/main/java/com/example/auth/GoogleAuthCallbackActivity.kt")
google_activity = google_activity_path.read_text()
if "val googleNonce = GoogleNonce.sha256Hex(rawNonce)" not in google_activity:
    google_activity = google_activity.replace("import android.util.Base64\n", "")
    google_activity = google_activity.replace("import java.security.SecureRandom\n", "")
    google_activity = replace_once(
        google_activity,
        """        val rawNonce = generateSecureRandomNonce()\n""",
        """        val rawNonce = GoogleNonce.generate()\n        val googleNonce = GoogleNonce.sha256Hex(rawNonce)\n""",
        "Google raw/hashed nonce creation",
    )
    google_activity = replace_once(
        google_activity,
        ".setNonce(rawNonce)",
        ".setNonce(googleNonce)",
        "Google hashed nonce request",
    )
    google_activity = re.sub(
        r"\n    private fun generateSecureRandomNonce\(byteLength: Int = 32\): String \{.*?\n    \}\n",
        "\n",
        google_activity,
        count=1,
        flags=re.S,
    )
google_activity_path.write_text(google_activity)

nonce_test_path = Path("app/src/test/java/com/example/auth/GoogleNonceTest.kt")
nonce_test_path.parent.mkdir(parents=True, exist_ok=True)
nonce_test_path.write_text(r'''package com.example.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleNonceTest {
    @Test
    fun sha256Hex_matchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            GoogleNonce.sha256Hex("abc")
        )
    }
}
''')


# -----------------------------------------------------------------------------
# Sign-in screen: make Remember functional and durable without plaintext storage.
# -----------------------------------------------------------------------------
auth_path = Path("app/src/main/java/com/example/ui/screens/AuthScreens.kt")
auth = auth_path.read_text()
auth = ensure_import(auth, "import androidx.compose.ui.platform.LocalContext")
auth = ensure_import(auth, "import com.example.auth.RememberedLoginStore")
auth = ensure_import(auth, "import com.example.data.local.rememberPersistentTextState")

old_identifier = '''    var emailOrUsername by rememberSaveable(initialIdentifier) {\n        mutableStateOf(initialIdentifier)\n    }\n\n    var password by remember {\n        mutableStateOf(\"\")\n    }\n'''
new_identifier = '''    val context = LocalContext.current\n    val rememberedLogin = remember { RememberedLoginStore.load(context) }\n\n    var emailOrUsername by rememberSaveable(initialIdentifier, rememberedLogin?.identifier) {\n        mutableStateOf(rememberedLogin?.identifier?.takeIf { it.isNotBlank() } ?: initialIdentifier)\n    }\n\n    var password by rememberSaveable {\n        mutableStateOf(rememberedLogin?.password.orEmpty())\n    }\n'''
if old_identifier in auth:
    auth = auth.replace(old_identifier, new_identifier, 1)

old_remember = '''    var rememberMe by remember {\n        mutableStateOf(true)\n    }\n'''
new_remember = '''    var rememberMe by rememberSaveable {\n        mutableStateOf(rememberedLogin != null || true)\n    }\n'''
if old_remember in auth:
    auth = auth.replace(old_remember, new_remember, 1)

scope_anchor = '''    val coroutineScope =\n        rememberCoroutineScope()\n'''
remember_effect = '''    val coroutineScope =\n        rememberCoroutineScope()\n\n    LaunchedEffect(rememberMe, emailOrUsername, password) {\n        delay(300)\n        if (rememberMe) {\n            if (emailOrUsername.isNotBlank() || password.isNotBlank()) {\n                RememberedLoginStore.save(context, emailOrUsername, password)\n            }\n        } else {\n            RememberedLoginStore.clear(context)\n        }\n    }\n'''
if "RememberedLoginStore.save(context, emailOrUsername, password)" not in auth:
    auth = replace_once(auth, scope_anchor, remember_effect, "Remember-login persistence effect")

auth = auth.replace('''                            onCheckedChange = {\n                                rememberMe = it\n                            },''', '''                            onCheckedChange = { checked ->\n                                rememberMe = checked\n                                if (!checked) RememberedLoginStore.clear(context)\n                            },''', 1)
auth = auth.replace('''                            \"Keep me signed in\",''', '''                            \"Remember username & password\",''', 1)
auth_path.write_text(auth)


# -----------------------------------------------------------------------------
# Dynamic chat drafts: one encrypted draft per conversation, plus messages search.
# -----------------------------------------------------------------------------
messages_path = Path("app/src/main/java/com/example/ui/screens/MessagesScreen.kt")
messages = messages_path.read_text()
messages = ensure_import(messages, "import com.example.data.local.rememberPersistentTextState")
messages = messages.replace(
    '''    var messageText by rememberSaveable {\n        mutableStateOf(\"\")\n    }''',
    '''    var messageText by rememberPersistentTextState(\n        key = \"chat_message\",\n        scope = convo.id.ifBlank { convo.partnerUsername }\n    )''',
    1,
)
messages = messages.replace(
    '''    var searchQuery by rememberSaveable {\n        mutableStateOf(\"\")\n    }''',
    '''    var searchQuery by rememberPersistentTextState(key = \"messages_search\")''',
    1,
)
messages_path.write_text(messages)


# -----------------------------------------------------------------------------
# Comment draft: scope text to the post so drafts never leak between posts.
# -----------------------------------------------------------------------------
comment_path = Path("app/src/main/java/com/example/ui/components/CommentSheet.kt")
comment = comment_path.read_text()
comment = ensure_import(comment, "import com.example.data.local.rememberPersistentTextState")
if "draftKey: String," not in comment:
    comment = replace_once(
        comment,
        '''    currentUserId: String,\n    mentionCandidates: List<UserProfile>,''',
        '''    currentUserId: String,\n    draftKey: String,\n    mentionCandidates: List<UserProfile>,''',
        "Comment draft key parameter",
    )
comment = comment.replace(
    '''    var textInput by remember { mutableStateOf(\"\") }''',
    '''    var textInput by rememberPersistentTextState(key = \"comment_text\", scope = draftKey)''',
    1,
)
comment_path.write_text(comment)

main_path = Path("app/src/main/java/com/example/MainActivity.kt")
main = main_path.read_text()
if "draftKey = uiState.activeCommentsPostId.orEmpty()" not in main:
    main = replace_once(
        main,
        '''                currentUserId = uiState.myProfile.id,\n                mentionCandidates =''',
        '''                currentUserId = uiState.myProfile.id,\n                draftKey = uiState.activeCommentsPostId.orEmpty(),\n                mentionCandidates =''',
        "Comment post-scoped draft wiring",
    )
main_path.write_text(main)


# -----------------------------------------------------------------------------
# Create-post text and poll options are high-value drafts and need profile scope.
# -----------------------------------------------------------------------------
create_post_path = Path("app/src/main/java/com/example/ui/components/CreatePostSheet.kt")
create_post = create_post_path.read_text()
create_post = ensure_import(create_post, "import com.example.data.local.rememberPersistentTextState")
create_post = ensure_import(create_post, "import com.example.data.local.rememberPersistentStringListState")
create_post = create_post.replace(
    '''    var text by rememberSaveable { mutableStateOf(\"\") }''',
    '''    var text by rememberPersistentTextState(\n        key = \"create_post_text\",\n        scope = profile.id.ifBlank { profile.username }\n    )''',
    1,
)
create_post = create_post.replace(
    '''    var pollQuestion by rememberSaveable { mutableStateOf(\"\") }''',
    '''    var pollQuestion by rememberPersistentTextState(\n        key = \"create_post_poll_question\",\n        scope = profile.id.ifBlank { profile.username }\n    )''',
    1,
)
create_post = create_post.replace(
    '''    var pollOptions by rememberSaveable { mutableStateOf(listOf(\"\", \"\")) }''',
    '''    var pollOptions by rememberPersistentStringListState(\n        key = \"create_post_poll_options\",\n        initialValue = listOf(\"\", \"\"),\n        scope = profile.id.ifBlank { profile.username }\n    )''',
    1,
)
create_post_path.write_text(create_post)


# -----------------------------------------------------------------------------
# Broad coverage: migrate simple empty-string Compose input state across UI files.
# This catches search, seller, market, profile, report, story, Connect and settings
# fields while leaving non-text state untouched. Chat/comment are handled above.
# -----------------------------------------------------------------------------
state_pattern = re.compile(
    r'(?P<indent>^[ \t]*)var\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s+by\s+'
    r'remember(?:Saveable)?(?:\([^\n{}]*\))?\s*\{\s*mutableStateOf\(\s*""\s*\)\s*\}',
    flags=re.M,
)

skip_paths = {messages_path.resolve(), comment_path.resolve(), create_post_path.resolve()}
changed_files = []
for path in sorted(Path("app/src/main/java/com/example/ui").rglob("*.kt")):
    if path.resolve() in skip_paths:
        continue
    text = path.read_text()
    replacements = []
    occurrence = 0
    for match in state_pattern.finditer(text):
        name = match.group("name")
        if not re.search(rf'\bvalue\s*=\s*{re.escape(name)}\b', text):
            continue
        occurrence += 1
        key = f"{path.relative_to(Path('app/src/main/java')).as_posix()}:{name}:{occurrence}"
        replacement = f'{match.group("indent")}var {name} by rememberPersistentTextState(key = "{key}")'
        replacements.append((match.start(), match.end(), replacement))
    if not replacements:
        continue
    for start, end, replacement in reversed(replacements):
        text = text[:start] + replacement + text[end:]
    text = ensure_import(text, "import com.example.data.local.rememberPersistentTextState")
    path.write_text(text)
    changed_files.append(str(path))

print(f"Persistent text migration updated {len(changed_files)} additional UI files")
for path in changed_files:
    print(f"  - {path}")
