package com.example.sharing

import android.net.Uri
import androidx.navigation.NavHostController
import com.example.BuildConfig

data class AppDeepLink(
    val type: ShareContentType,
    val id: String
)

object DeepLinkRouter {
    private val configuredBase: Uri
        get() = Uri.parse(BuildConfig.SHARE_BASE_URL)

    private val acceptedWebBases: List<Uri>
        get() = listOf(
            configuredBase,
            Uri.parse("https://www.blink.com.ng"),
            Uri.parse("https://blink.com.ng"),
            Uri.parse("https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/blink-web"),
            Uri.parse("https://glimmar-code.github.io/Blinkng")
        ).distinctBy { "${it.host.orEmpty().lowercase()}${it.path.orEmpty().trimEnd('/')}" }

    fun parse(uri: Uri?): AppDeepLink? {
        if (uri == null) return null

        return when {
            uri.scheme.equals("blink", ignoreCase = true) -> parseAppScheme(uri)
            uri.scheme.equals("https", ignoreCase = true) -> parseWebUrl(uri)
            else -> null
        }
    }

    private fun parseAppScheme(uri: Uri): AppDeepLink? {
        val type = ShareContentType.fromPath(uri.host) ?: return null
        val id = uri.pathSegments.firstOrNull()?.trim()?.removePrefix("@") ?: return null
        return validated(type, id)
    }

    private fun parseWebUrl(uri: Uri): AppDeepLink? {
        val incoming = uri.pathSegments.filter { it.isNotBlank() }
        val base = acceptedWebBases.firstOrNull { candidate ->
            if (!uri.host.equals(candidate.host, ignoreCase = true)) return@firstOrNull false
            val candidateSegments = candidate.pathSegments.filter { it.isNotBlank() }
            incoming.size >= candidateSegments.size &&
                incoming.take(candidateSegments.size).map { it.lowercase() } ==
                    candidateSegments.map { it.lowercase() }
        } ?: return null

        val baseSegments = base.pathSegments.filter { it.isNotBlank() }
        val segments = incoming.drop(baseSegments.size)
        if (segments.size == 1 && segments[0].startsWith("@")) {
            return validated(ShareContentType.PROFILE, segments[0].removePrefix("@"))
        }
        if (segments.size != 2) return null

        val type = ShareContentType.fromPath(segments[0]) ?: return null
        return validated(type, segments[1].removePrefix("@"))
    }

    private fun validated(type: ShareContentType, rawId: String): AppDeepLink? {
        val id = rawId.trim()
        if (id.isBlank() || id.length > 128) return null
        return AppDeepLink(type = type, id = id)
    }

    /** Navigation Compose equivalent for screens backed by a NavHost. */
    fun navigate(navController: NavHostController, deepLink: AppDeepLink) {
        val encoded = Uri.encode(deepLink.id)
        val route = when (deepLink.type) {
            ShareContentType.PROFILE -> "profile/$encoded"
            ShareContentType.POST -> "post/$encoded"
            ShareContentType.REEL -> "reel/$encoded"
        }
        navController.navigate(route) { launchSingleTop = true }
    }
}
