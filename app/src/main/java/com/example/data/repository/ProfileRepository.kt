package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.models.ContactField
import com.example.data.models.UserProfile
import com.example.data.supabase.ProfileMediaType
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileRepository(
    private val supabaseService: SupabaseService =
        SupabaseService()
) {

    companion object {
        private const val TAG =
            "ProfileRepository"
    }

    suspend fun fetchCurrent(
        username: String
    ): UserProfile? =
        withContext(Dispatchers.IO) {
            try {
                fetchByUsername(
                    username
                )
            } catch (
                e: Exception
            ) {
                Log.e(
                    TAG,
                    "fetchCurrent error",
                    e
                )
                null
            }
        }

    suspend fun fetchById(
        userId: String
    ): UserProfile? =
        withContext(Dispatchers.IO) {

            try {

                if (
                    userId.isBlank()
                ) {
                    return@withContext null
                }

                val profile =
                    supabaseService
                        .fetchProfileById(
                            userId
                        )

                profile

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "fetchById error",
                    e
                )

                null
            }
        }

    suspend fun fetchByUsername(
        username: String
    ): UserProfile? =
        withContext(Dispatchers.IO) {

            try {

                val cleanUser =
                    username
                        .trim()
                        .lowercase()
                        .removePrefix("@")

                if (
                    cleanUser.isBlank()
                ) {
                    return@withContext null
                }

                supabaseService
                    .fetchProfileByUsername(
                        cleanUser
                    )

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "fetchByUsername error",
                    e
                )

                null
            }
        }

    suspend fun updateProfile(
        profile: UserProfile
    ): Boolean =
        withContext(Dispatchers.IO) {

            try {

                val success =
                    supabaseService
                        .updateProfile(
                            profile
                        )

                Log.d(
                    TAG,
                    "updateProfile success=$success user=${profile.id}"
                )

                success

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "updateProfile error",
                    e
                )

                false
            }
        }

    suspend fun ensureProfile(
        userId: String,
        email: String,
        username: String,
        fullName: String,
        faculty: String? = null,
        university: String? = null
    ): UserProfile =
        withContext(Dispatchers.IO) {

            supabaseService
                .ensureAuthenticatedProfile(
                    userId =
                        userId,
                    email =
                        email,
                    username =
                        username,
                    fullName =
                        fullName,
                    faculty =
                        faculty,
                    university =
                        university
                )
        }

    suspend fun searchProfiles(
        query: String
    ): List<UserProfile> =
        withContext(Dispatchers.IO) {

            if (
                query.isBlank()
            ) {
                return@withContext emptyList()
            }

            try {

                supabaseService
                    .searchProfiles(
                        query
                    )

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "searchProfiles error",
                    e
                )

                emptyList()
            }
        }

    suspend fun uploadProfileMedia(
        context: Context,
        uri: Uri,
        userId: String,
        type: ProfileMediaType
    ): String? =
        withContext(Dispatchers.IO) {

            try {

                val resolver =
                    context.contentResolver

                val mimeType =
                    resolver.getType(
                        uri
                    )
                        ?: "image/jpeg"

                val bytes =
                    resolver
                        .openInputStream(
                            uri
                        )
                        ?.use {
                            it.readBytes()
                        }

                if (
                    bytes == null ||
                    bytes.isEmpty()
                ) {

                    Log.e(
                        TAG,
                        "No image bytes available for $type"
                    )

                    return@withContext null
                }

                supabaseService
                    .uploadProfileMedia(
                        userId =
                            userId,
                        bytes =
                            bytes,
                        mimeType =
                            mimeType,
                        type =
                            type
                    )

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "uploadProfileMedia error",
                    e
                )

                null
            }
        }

    suspend fun uploadAvatar(
        context: Context,
        uri: Uri,
        userId: String
    ): String? =
        uploadProfileMedia(
            context =
                context,
            uri =
                uri,
            userId =
                userId,
            type =
                ProfileMediaType.AVATAR
        )

    suspend fun uploadCover(
        context: Context,
        uri: Uri,
        userId: String
    ): String? =
        uploadProfileMedia(
            context =
                context,
            uri =
                uri,
            userId =
                userId,
            type =
                ProfileMediaType.COVER
        )
}