with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

idx_start = content.find('    fun updateProfile(')
idx_end = content.find('    fun updateMyProfile(', idx_start)

if idx_start != -1 and idx_end != -1:
    new_method = """    fun updateProfile(
        updated: UserProfile
    ) {
        viewModelScope.launch {
            try {
                val success = profileRepository.updateProfile(updated)
                if (success) {
                    _uiState.value =
                        _uiState.value.copy(
                            myProfile =
                                updated,
                            isEditProfileOpen =
                                false,
                            viewingProfile =
                                if (
                                    _uiState.value
                                        .viewingProfile
                                        ?.username
                                        ?.equals(
                                            updated.username,
                                            ignoreCase = true
                                        ) == true
                                ) {
                                    updated
                                } else {
                                    _uiState.value
                                        .viewingProfile
                                }
                        )

                    saveLocalProfile(
                        updated
                    )

                    updateLocalAuthorData(
                        updated
                    )

                    showToast(
                        "✅ Profile saved successfully."
                    )
                } else {
                    showToast("❌ Failed to update profile.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateProfile background sync error", e)
                showToast("❌ Failed to update profile: ${e.message}")
            }
        }
    }

"""
    content = content[:idx_start] + new_method + content[idx_end:]
    with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
        f.write(content)
else:
    print("Could not find start/end")

