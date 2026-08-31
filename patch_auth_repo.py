with open('app/src/main/java/com/example/data/repository/AuthRepository.kt', 'r') as f:
    content = f.read()

old_check = """    private fun checkCachedSession() {
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        if (isLoggedIn) {
            val email = prefs.getString("email", "golowosile@gmail.com") ?: "golowosile@gmail.com"
            val fullName = prefs.getString("full_name", "Gbolahan Olowosile") ?: "Gbolahan Olowosile"
            val username = prefs.getString("username", "golowosile") ?: "golowosile"
            val faculty = prefs.getString("faculty", "SIMME") ?: "SIMME"
            val university = prefs.getString("university", "University of Lagos") ?: "University of Lagos"
            val avatarUrl = prefs.getString("avatar_url", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&h=300&fit=crop") ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&h=300&fit=crop"

            val cachedProfile = UserProfile(
                email = ContactField(email, true),
                fullName = fullName,
                username = username,
                faculty = faculty,
                university = university,
                avatarUrl = avatarUrl
            )
            _authState.value = AuthState.Authenticated(cachedProfile)
        } else {
            _authState.value = AuthState.Unauthenticated()
        }
    }"""

new_check = """    private fun checkCachedSession() {
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val hasToken = !SupabaseService.accessToken().isNullOrBlank()
        if (isLoggedIn && hasToken) {
            val email = prefs.getString("email", "") ?: ""
            val fullName = prefs.getString("full_name", "") ?: ""
            val username = prefs.getString("username", "") ?: ""
            val faculty = prefs.getString("faculty", "") ?: ""
            val university = prefs.getString("university", "") ?: ""
            val avatarUrl = prefs.getString("avatar_url", "") ?: ""

            val cachedProfile = UserProfile(
                email = ContactField(email, true),
                fullName = fullName,
                username = username,
                faculty = faculty,
                university = university,
                avatarUrl = avatarUrl
            )
            _authState.value = AuthState.Authenticated(cachedProfile)
        } else {
            _authState.value = AuthState.Unauthenticated()
        }
    }"""

content = content.replace(old_check, new_check)

old_google = """            val effectiveEmail = email.trim().lowercase(Locale.US).ifBlank {
                "therealglimmar@gmail.com"
            }"""

new_google = """            val effectiveEmail = email.trim().lowercase(Locale.US)
            if (effectiveEmail.isBlank()) throw Exception("Email is required.")"""

content = content.replace(old_google, new_google)

with open('app/src/main/java/com/example/data/repository/AuthRepository.kt', 'w') as f:
    f.write(content)

