with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

import_statement = "import kotlinx.coroutines.sync.withLock\nimport kotlinx.coroutines.sync.Mutex\n"
content = content.replace("import kotlinx.coroutines.withContext", import_statement + "import kotlinx.coroutines.withContext")

# Add the executeRequest method inside the class
method = """
    private val refreshMutex = Mutex()

    private suspend fun executeRequest(request: Request): okhttp3.Response {
        val isAuthenticated = request.header("X-Authenticated") == "true"
        var activeRequest = request
        if (isAuthenticated) {
            activeRequest = request.newBuilder().removeHeader("X-Authenticated").build()
        }

        var response = withContext(Dispatchers.IO) { client.newCall(activeRequest).execute() }
        
        if (isAuthenticated && response.code == 401) {
            response.close()
            refreshMutex.withLock {
                val currentToken = activeRequest.header("Authorization")?.removePrefix("Bearer ") ?: ""
                val storedToken = accessToken() ?: anonKey
                
                if (storedToken != anonKey && storedToken != currentToken) {
                    // Token was refreshed by another thread
                    activeRequest = activeRequest.newBuilder()
                        .header("Authorization", "Bearer $storedToken")
                        .build()
                    response = withContext(Dispatchers.IO) { client.newCall(activeRequest).execute() }
                } else {
                    val refreshed = refreshSession()
                    if (refreshed) {
                        val refreshedToken = accessToken() ?: anonKey
                        activeRequest = activeRequest.newBuilder()
                            .header("Authorization", "Bearer $refreshedToken")
                            .build()
                        response = withContext(Dispatchers.IO) { client.newCall(activeRequest).execute() }
                    } else {
                        clearSession()
                        throw java.io.IOException("Unauthorized - Refresh failed")
                    }
                }
            }
        }
        return response
    }
"""

content = content.replace("private fun requestJson(", method + "\n    private fun requestJson(")

# Modify newRequestBuilder
old_builder = """        val builder =
            Request.Builder()
                .url(fullUrl)
                .addHeader(
                    "apikey",
                    anonKey
                )
                .addHeader(
                    "Accept",
                    "application/json"
                )

        val token =
            if (authenticated) {
                accessToken()
                    ?.takeIf { it.isNotBlank() }
                    ?: anonKey
            } else {
                anonKey
            }

        builder.addHeader(
            "Authorization",
            "Bearer $token"
        )

        return builder"""

new_builder = """        val builder =
            Request.Builder()
                .url(fullUrl)
                .addHeader(
                    "apikey",
                    anonKey
                )
                .addHeader(
                    "Accept",
                    "application/json"
                )
                
        if (authenticated) {
            builder.addHeader("X-Authenticated", "true")
        }

        val token =
            if (authenticated) {
                accessToken()
                    ?.takeIf { it.isNotBlank() }
                    ?: anonKey
            } else {
                anonKey
            }

        builder.addHeader(
            "Authorization",
            "Bearer $token"
        )

        return builder"""

content = content.replace(old_builder, new_builder)

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)

