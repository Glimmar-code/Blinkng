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
        val base = configuredBase
        if (!uri.host.equals(base.host, ignoreCase = true)) return null

        val baseSegments = base.pathSegments.filter { it.isNotBlank() }
        val incoming = uri.pathSegments.filter { it.isNotBlank() }
        if (incoming.size < baseSegments.size) return null
        if (incoming.take(baseSegments.size).map { it.lowercase() } != baseSegments.map { it.lowercase() }) return null

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
