import re
with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

new_success_block = """                val success = profileRepository.updateProfile(updated)
                if (success) {
                    // Fetch authoritative profile after update
                    val authoritativeProfile = profileRepository.fetchProfile() ?: updated
                    
                    _uiState.value =
                        _uiState.value.copy(
                            myProfile =
                                authoritativeProfile,
                            isEditProfileOpen =
                                false,
                            viewingProfile =
                                if (
                                    _uiState.value
                                        .viewingProfile
                                        ?.username
                                        ?.equals(
                                            authoritativeProfile.username,
                                            ignoreCase = true
                                        ) == true
                                ) {
                                    authoritativeProfile
                                } else {
                                    _uiState.value
                                        .viewingProfile
                                }
                        )

                    saveLocalProfile(
                        authoritativeProfile
                    )

                    updateLocalAuthorData(
                        authoritativeProfile
                    )

                    showToast(
                        "✅ Profile saved successfully."
                    )
                }"""

content = re.sub(r'                val success = profileRepository\.updateProfile\(updated\)\s+if \(success\) \{[\s\S]*?showToast\(\s*"✅ Profile saved successfully\."\s*\)\s*\}', new_success_block, content)

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
    f.write(content)

