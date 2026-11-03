package com.example.media

import androidx.core.content.FileProvider

/**
 * Distinct provider class avoids merging our chat grant paths into Godot's
 * androidx.core.content.FileProvider entry and preserves the game provider.
 */
class BlinkChatFileProvider : FileProvider()
