package com.example.data.models

import com.blinkng.shared.discovery.BlinkNigerianUniversities

/** Backwards-compatible Android alias to the cross-platform university catalogue. */
object NigerianUniversities {
    val all: List<String> get() = BlinkNigerianUniversities.all
}
