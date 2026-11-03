package com.blinkng.shared.ui.editor

/**
 * Core data classes and helper utilities shared between Android and Windows editors.
 * The implementations are intentionally lightweight – they only hold configuration
 * data. Real image/video processing is performed by platform‑specific adapters.
 */

// Crop configuration – aspect ratio (width:height) and optional rotation in degrees.
data class CropConfig(
    val aspectRatio: Float? = null, // null = free form
    val rotationDegrees: Int = 0
)

// Basic filter configuration – a name identifier; actual filter implementation
// lives in platform code.
enum class FilterType { NONE, VIVID, PORTRAIT, MONOCHROME, SEPIA }

data class FilterConfig(
    val type: FilterType = FilterType.NONE,
    val intensity: Float = 1f // 0..1 range for filters that support intensity
)

// Adjustments – typical photo edit sliders.
data class AdjustConfig(
    val brightness: Float = 0f, // -1..1
    val contrast: Float = 1f,   // 0..2
    val saturation: Float = 1f, // 0..2
    val exposure: Float = 0f,   // -1..1
    val warmth: Float = 0f      // -1..1
)

// Text overlay placed on the image.
data class TextOverlay(
    val text: String,
    val color: Long, // ARGB color as Long
    val sizeSp: Float = 14f,
    val x: Float = 0f,
    val y: Float = 0f
)

// Sticker overlay – an emoji or asset identifier.
data class StickerOverlay(
    val sticker: String, // e.g. "❤️" or asset name
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f
)

// Video trimming configuration.
data class VideoTrim(
    val startMs: Long = 0L,
    val endMs: Long = 0L // 0 = till end
)

// Video speed configuration.
enum class VideoSpeed(val multiplier: Float) {
    HALF(0.5f), NORMAL(1f), ONE_AND_A_HALF(1.5f), DOUBLE(2f)
}

// Audio configuration for video (mute / volume).
data class VideoAudioConfig(
    val muted: Boolean = false,
    val volume: Float = 1f // 0..1
)

/** Compression quality for the final exported media. */
enum class CompressionQuality {
    LOW, MEDIUM, HIGH
}

/**
 * Stub helper – in a real implementation this would apply the supplied filter to a bitmap.
 * The shared module cannot reference Android graphics classes, so we keep the signature
 * generic (Any) and let the platform provide an actual implementation via expect/actual.
 */
expect fun applyFilter(source: Any, config: FilterConfig): Any
expect fun applyAdjustments(source: Any, config: AdjustConfig): Any
expect fun applyCrop(source: Any, config: CropConfig): Any
expect fun applyBlur(source: Any, radius: Float): Any

/** Helper for video trimming – returns a placeholder object representing the trimmed clip. */
expect fun trimVideo(source: Any, trim: VideoTrim): Any
