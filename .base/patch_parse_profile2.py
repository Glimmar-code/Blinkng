with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

idx_start = content.find('private fun parseUserProfile(')
idx_end = content.find('suspend fun updateProfile(', idx_start)

new_parse = """private fun parseUserProfile(obj: JSONObject): UserProfile {
        val badge = when (obj.optString("verification_badge", "").uppercase(Locale.US)) {
            "GOLD" -> VerificationBadge.GOLD
            "BLUE" -> VerificationBadge.BLUE
            else -> VerificationBadge.NONE
        }

        val links = SocialLinks(
            website = obj.optString("website", ""),
            linkedin = obj.optString("linkedin", ""),
            twitter = obj.optString("twitter", ""),
            instagram = obj.optString("instagram", ""),
            featuredLink = obj.optString("featured_link", ""),
            featuredLinkLabel = obj.optString("featured_link_label", "")
        )

        val skills = mutableListOf<String>()
        obj.optJSONArray("core_skills")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotBlank()) skills.add(s)
            }
        }
        
        val hobbiesList = mutableListOf<String>()
        obj.optJSONArray("hobbies")?.let { arr ->
            for (i in 0 until arr.length()) {
                val h = arr.optString(i, "")
                if (h.isNotBlank()) hobbiesList.add(h)
            }
        }
        
        val languagesList = mutableListOf<String>()
        obj.optJSONArray("languages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val l = arr.optString(i, "")
                if (l.isNotBlank()) languagesList.add(l)
            }
        }
        
        val endorsementsList = mutableListOf<SkillEndorsement>()
        obj.optJSONArray("skill_endorsements")?.let { arr ->
            for (i in 0 until arr.length()) {
                val seObj = arr.optJSONObject(i)
                if (seObj != null) {
                    endorsementsList.add(
                        SkillEndorsement(
                            skill = seObj.optString("skill", ""),
                            endorsements = seObj.optInt("endorsements", 0),
                            endorsedByMe = seObj.optBoolean("endorsed_by_me", false)
                        )
                    )
                }
            }
        }
        
        val badgesList = mutableListOf<AchievementBadge>()
        obj.optJSONArray("badges")?.let { arr ->
            for (i in 0 until arr.length()) {
                val bObj = arr.optJSONObject(i)
                if (bObj != null) {
                    badgesList.add(
                        AchievementBadge(
                            id = bObj.optString("id", ""),
                            title = bObj.optString("title", ""),
                            description = bObj.optString("description", ""),
                            iconName = bObj.optString("iconName", "trophy")
                        )
                    )
                }
            }
        }
        
        val availabilityStr = obj.optString("custom_status", "")
        val availabilityStatus = try {
            if (availabilityStr.isNotBlank()) {
                AvailabilityStatus.valueOf(availabilityStr)
            } else {
                AvailabilityStatus.NONE
            }
        } catch (e: Exception) {
            AvailabilityStatus.NONE
        }

        return UserProfile(
            id = obj.optString("id", ""),
            fullName = obj.optString("full_name", obj.optString("name", "")),
            username = obj.optString("username", ""),
            avatarUrl = obj.optString("avatar_url", ""),
            coverPhotoUrl = obj.optString("cover_photo", obj.optString("cover_photo_url", obj.optString("cover_url", ""))),
            verificationBadge = badge,
            professionalHeadline = obj.optString("professional_headline", ""),
            currentJobTitle = obj.optString("current_job_title", ""),
            university = obj.optString("university", ""),
            faculty = obj.optString("faculty", ""),
            department = obj.optString("department", ""),
            courseOfStudy = obj.optString("course_of_study", ""),
            academicLevel = obj.optString("academic_level", ""),
            graduationYear = obj.optString("graduation_year", ""),
            bio = obj.optString("bio", ""),
            availability = availabilityStatus,
            countryOfOrigin = obj.optString("country_of_origin", ""),
            currentCityState = obj.optString("current_city_state", ""),
            email = ContactField(obj.optString("email", ""), true),
            phone = ContactField(obj.optString("phone", ""), true),
            whatsapp = ContactField(obj.optString("whatsapp", ""), true),
            links = links,
            coreSkills = skills,
            skillEndorsements = endorsementsList,
            hobbies = hobbiesList,
            languages = languagesList,
            favoriteQuote = obj.optString("favorite_quote", ""),
            followerCount = obj.optInt("follower_count", 0),
            followingCount = obj.optInt("following_count", 0),
            profileViewsThisWeek = obj.optInt("profile_views_this_week", obj.optInt("profile_views", 0)),
            dailyStreak = obj.optInt("daily_streak", 0),
            worldRank = obj.optInt("world_rank", 0),
            campusRank = obj.optInt("campus_rank", 0),
            onlineNow = obj.optBoolean("online_now", false),
            verifiedAtMillis = obj.optLong("verified_at_millis", 0L),
            joinedLabel = obj.optString("joined_label", ""),
            isSellerActive = obj.optBoolean("is_seller_active", false),
            sellerStoreName = obj.optString("seller_store_name", ""),
            badges = badgesList
        )
    }

    """

if idx_start != -1 and idx_end != -1:
    content = content[:idx_start] + new_parse + content[idx_end:]
    with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
        f.write(content)
else:
    print("Could not find start/end")

