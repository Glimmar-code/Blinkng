package com.example.data.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class BlinkGalleryPhoto(
    val uri: Uri, val album: String, val name: String
)

internal fun hasGalleryPermission(context: Context): Boolean {
    fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 34 ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

/**
 * Limited, local MediaStore discovery only after the user grants media access.
 * Android's privacy-scoped Photo Picker stays available without any permission.
 * Never reads media bytes or uploads pictures while browsing the sheet.
 */
internal suspend fun readAvailableGalleryPhotos(context: Context): List<BlinkGalleryPhoto> =
    withContext(Dispatchers.IO) {
        if (!hasGalleryPermission(context)) return@withContext emptyList()
        val output = ArrayList<BlinkGalleryPhoto>(80)
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        runCatching {
            context.contentResolver.query(
                uri, projection, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val albumIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val fileNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext() && output.size < 80) {
                    val id = cursor.getLong(idIndex)
                    val album = if (albumIndex >= 0) cursor.getString(albumIndex)
                        ?: "Gallery" else "Gallery"
                    val name = if (fileNameIndex >= 0) cursor.getString(fileNameIndex)
                        .orEmpty() else ""
                    output.add(BlinkGalleryPhoto(ContentUris.withAppendedId(uri, id), album, name))
                }
            }
        }
        output
    }

