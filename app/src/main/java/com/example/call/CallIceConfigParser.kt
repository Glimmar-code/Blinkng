package com.example.call

import org.json.JSONArray
import org.json.JSONObject

/**
 * Decodes both BLINK's call-scoped TURN endpoint and the previously deployed
 * authenticated coturn endpoint during a controlled migration.
 *
 * Never logs credentials and never treats STUN-only responses as ready for
 * production-grade calling.
 */
internal object CallIceConfigParser {
    data class Server(val urls: List<String>, val username: String = "", val credential: String = "")

    /**
     * A missing endpoint is different from a 404 for a caller who does not
     * belong to the call. Never turn an authorization/membership rejection
     * into a request to the older account-scoped fallback endpoint.
     */
    fun allowLegacyFallback(statusCode: Int, responseBody: String): Boolean {
        if (statusCode == 404) {
            return responseBody.contains("Requested function was not found", ignoreCase = true) ||
                (responseBody.contains("\\"code\\":\\"NOT_FOUND\\"") &&
                    responseBody.contains("function", ignoreCase = true))
        }
        return statusCode == 503 &&
            responseBody.contains("TURN relay is not configured", ignoreCase = true)
    }

    fun parse(body: String): List<Server> {
        val root = JSONObject(body)
        val entries = root.optJSONArray("ice_servers")
        val parsed = if (entries != null) {
            buildList {
                for (i in 0 until minOf(entries.length(), 12)) {
                    val row = entries.optJSONObject(i) ?: continue
                    server(row.opt("urls"), row.optString("username"), row.optString("credential"))
                        ?.let(::add)
                }
            }
        } else {
            listOfNotNull(
                server(root.opt("urls"), root.optString("username"), root.optString("credential")),
            )
        }
        require(parsed.any { item ->
            item.username.isNotBlank() && item.credential.isNotBlank() &&
                item.urls.any { it.startsWith("turn:") || it.startsWith("turns:") }
        }) { "No authenticated TURN relay was returned" }
        return parsed
    }

    private fun server(rawUrls: Any?, username: String, credential: String): Server? {
        val urls = when (rawUrls) {
            is JSONArray -> (0 until minOf(rawUrls.length(), 8)).mapNotNull {
                rawUrls.optString(it).takeIf(String::isNotBlank)
            }
            is String -> listOf(rawUrls)
            else -> emptyList()
        }.map(String::trim).filter { url ->
            (url.startsWith("stun:") || url.startsWith("stuns:") ||
                url.startsWith("turn:") || url.startsWith("turns:")) &&
                !url.any(Char::isWhitespace)
        }
        if (urls.isEmpty()) return null
        if (urls.any { it.startsWith("turn:") || it.startsWith("turns:") } &&
            (username.isBlank() || credential.isBlank())) return null
        return Server(urls, username, credential)
    }
}
