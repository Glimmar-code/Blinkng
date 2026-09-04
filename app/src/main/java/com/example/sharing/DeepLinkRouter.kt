package com.example.sharing

import android.net.Uri
import androidx.navigation.NavHostController
import com.example.BuildConfig

data class AppDeepLink(
    val type: ShareContentType,
    val id: String
)

object DeepLinkRouter {
    private val expectedHost: String
        get() = Uri.parse(BuildConfig.SHARE_BASE_URL).host.orEmpty()

    fun parse(uri: Uri?): AppDeepLink? {
        if (uri == null || !uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.host.equals(expectedHost, ignoreCase = true)) return null

        val segments = uri.pathSegments.filter { it.isNotBlank() }
        if (segments.size != 2) return null

        val type = ShareContentType.fromPath(segments[0]) ?: return null
        val id = segments[1].trim().removePrefix("@")
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
