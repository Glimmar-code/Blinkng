import re

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

idx_start = content.find('suspend fun updateProfile(')
idx_end = content.find('// ============================================================', idx_start)

new_update_profile = """suspend fun updateProfile(
        profile: UserProfile
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val currentUserId =
                    getCurrentUserId()
                        ?: profile.id.takeIf { it.isNotBlank() }

                val json =
                    JSONObject().apply {
                        put("full_name", profile.fullName.trim())
                        put("username", profile.username.trim().lowercase(Locale.US))
                        put("avatar_url", profile.avatarUrl)
                        put("cover_photo", profile.coverPhotoUrl)
                        put("professional_headline", profile.professionalHeadline)
                        put("current_job_title", profile.currentJobTitle)
                        put("bio", profile.bio)
                        put("university", profile.university)
                        put("faculty", profile.faculty)
                        put("department", profile.department)
                        put("course_of_study", profile.courseOfStudy)
                        put("academic_level", profile.academicLevel)
                        put("graduation_year", profile.graduationYear)
                        put("country_of_origin", profile.countryOfOrigin)
                        put("current_city_state", profile.currentCityState)
                        put("email", profile.email.value)
                        put("phone", profile.phone.value)
                        put("whatsapp", profile.whatsapp.value)
                        put("website", profile.links.website)
                        put("linkedin", profile.links.linkedin)
                        put("twitter", profile.links.twitter)
                        put("instagram", profile.links.instagram)
                        put("featured_link", profile.links.featuredLink)
                        put("featured_link_label", profile.links.featuredLinkLabel)
                        put("favorite_quote", profile.favoriteQuote)
                        put("custom_status", profile.availability.name)
                        put("profile_views_this_week", profile.profileViewsThisWeek)
                        put("verification_badge", profile.verificationBadge.name)
                        put("is_verified", profile.verificationBadge != VerificationBadge.NONE)
                        put("updated_at", nowIso())
                        
                        val skillsArray = JSONArray()
                        profile.coreSkills.forEach { skillsArray.put(it) }
                        put("core_skills", skillsArray)
                        
                        val hobbiesArray = JSONArray()
                        profile.hobbies.forEach { hobbiesArray.put(it) }
                        put("hobbies", hobbiesArray)
                        
                        val languagesArray = JSONArray()
                        profile.languages.forEach { languagesArray.put(it) }
                        put("languages", languagesArray)
                        
                        val skillEndorsementsArray = JSONArray()
                        profile.skillEndorsements.forEach {
                            val seObj = JSONObject()
                            seObj.put("skill", it.skill)
                            seObj.put("endorsements", it.endorsements)
                            seObj.put("endorsed_by_me", it.endorsedByMe)
                            skillEndorsementsArray.put(seObj)
                        }
                        put("skill_endorsements", skillEndorsementsArray)
                        
                        val badgesArray = JSONArray()
                        profile.badges.forEach {
                            val bObj = JSONObject()
                            bObj.put("id", it.id)
                            bObj.put("title", it.title)
                            bObj.put("description", it.description)
                            bObj.put("iconName", it.iconName)
                            badgesArray.put(bObj)
                        }
                        put("badges", badgesArray)
                        
                        put("daily_streak", profile.dailyStreak)
                        put("world_rank", profile.worldRank)
                        put("campus_rank", profile.campusRank)
                        put("verified_at_millis", profile.verifiedAtMillis)
                        put("is_seller_active", profile.isSellerActive)
                        put("seller_store_name", profile.sellerStoreName)
                        put("joined_label", profile.joinedLabel)
                    }

                // 1. Try update by user_id if present and valid UUID
                if (!currentUserId.isNullOrBlank() && isValidUuid(currentUserId)) {
                    val encodedId = encodeValue(currentUserId)
                    val request = newRequestBuilder("/rest/v1/profiles?id=eq.$encodedId", authenticated = true)
                        .addHeader("Prefer", "return=representation")
                        .patch(json.toString().toRequestBody(jsonMediaType))
                        .build()

                    val (success, body) = executeRequest(request).use { resp ->
                        Pair(resp.isSuccessful, resp.body?.string().orEmpty())
                    }
                    if (success && body.isNotBlank() && body != "[]") {
                        Log.d(TAG, "PROFILE_UPDATE success by ID: $currentUserId")
                        return@withContext true
                    }
                }

                // 2. Try update by username
                val cleanUser = profile.username.trim().lowercase(Locale.US)
                if (cleanUser.isNotBlank()) {
                    val reqUser = newRequestBuilder("/rest/v1/profiles?username=eq.${encodeValue(cleanUser)}", authenticated = true)
                        .addHeader("Prefer", "return=representation")
                        .patch(json.toString().toRequestBody(jsonMediaType))
                        .build()

                    val (success, body) = executeRequest(reqUser).use { resp ->
                        Pair(resp.isSuccessful, resp.body?.string().orEmpty())
                    }
                    if (success && body.isNotBlank() && body != "[]") {
                        Log.d(TAG, "PROFILE_UPDATE success by username: $cleanUser")
                        return@withContext true
                    }
                }

                // 3. Upsert via POST
                val validProfileId = if (isValidUuid(currentUserId)) {
                    currentUserId!!
                } else if (isValidUuid(getCurrentUserId())) {
                    getCurrentUserId()!!
                } else {
                    UUID.randomUUID().toString()
                }

                val upsertJson = JSONObject(json.toString()).apply {
                    put("id", validProfileId)
                }

                val upsertReq = newRequestBuilder("/rest/v1/profiles", authenticated = true)
                    .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                    .post(upsertJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val (upsertSuccess, _) = executeRequest(upsertReq).use { resp ->
                    Pair(resp.isSuccessful, resp.body?.string().orEmpty())
                }

                Log.d(TAG, "PROFILE_UPDATE upsert result: $upsertSuccess")
                return@withContext upsertSuccess

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "PROFILE_UPDATE exception",
                    e
                )
                return@withContext false
            }
        }
    """

if idx_start != -1 and idx_end != -1:
    content = content[:idx_start] + new_update_profile + content[idx_end:]
    with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
        f.write(content)
else:
    print("Could not find start/end")

