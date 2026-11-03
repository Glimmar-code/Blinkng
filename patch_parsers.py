with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

idx_start = content.find('    private fun parseUserProfile(')

parsers = """
    private fun parseFeedPost(obj: JSONObject): FeedPost {
        val author = obj.optString("author", "")
        val imagesArray = obj.optJSONArray("images")
        val imagesList = mutableListOf<String>()
        if (imagesArray != null) {
            for (i in 0 until imagesArray.length()) {
                val url = imagesArray.optString(i, "")
                if (url.isNotBlank()) imagesList.add(url)
            }
        }
        val isVerified = obj.optBoolean("is_verified", false) || obj.optString("verification_badge", "").equals("BLUE", ignoreCase = true) || obj.optString("verification_badge", "").equals("GOLD", ignoreCase = true)
        val badgeStr = obj.optString("verification_badge", "").uppercase(Locale.US)
        val badge = when (badgeStr) {
            "GOLD" -> VerificationBadge.GOLD
            "BLUE" -> VerificationBadge.BLUE
            else -> if (isVerified) VerificationBadge.BLUE else VerificationBadge.NONE
        }

        return FeedPost(
            id = obj.optString("id", ""),
            author = author,
            authorAvatar = obj.optString("author_avatar", ""),
            facultyTag = obj.optString("faculty_tag", obj.optString("faculty", "")),
            isVerified = isVerified,
            verificationBadge = badge,
            timeAgo = obj.optString("time_ago", "Recently"),
            text = obj.optString("text", obj.optString("content", "")),
            images = imagesList,
            likes = obj.optInt("likes_count", obj.optInt("likes", 0)),
            isLiked = obj.optBoolean("is_liked", false),
            commentsCount = obj.optInt("comments_count", 0),
            sharesCount = obj.optInt("shares_count", 0),
            viewsCount = obj.optInt("views_count", 0),
            isBookmarked = obj.optBoolean("is_bookmarked", false),
            isReel = obj.optBoolean("is_reel", false),
            videoDuration = obj.optString("video_duration", "0:00"),
            videoUrl = obj.optString("video_url", null),
            audience = obj.optString("audience", "Everyone"),
            category = obj.optString("category", "Campus Life"),
            location = obj.optString("location", null),
            linkUrl = obj.optString("link_url", null),
            allowComments = obj.optBoolean("allow_comments", true),
            hideLikes = obj.optBoolean("hide_likes", false),
            isPinned = obj.optBoolean("is_pinned", false),
            isDisappearing = obj.optBoolean("is_disappearing", false)
        )
    }

    private fun parseLeaderboardUser(obj: JSONObject, rank: Int): LeaderboardUser {
        val badgeStr = obj.optString("verification_badge", "").uppercase(Locale.US)
        val badge = when (badgeStr) {
            "GOLD" -> VerificationBadge.GOLD
            "BLUE" -> VerificationBadge.BLUE
            else -> if (obj.optBoolean("is_verified", false)) VerificationBadge.BLUE else VerificationBadge.NONE
        }
        return LeaderboardUser(
            rank = rank,
            username = obj.optString("username", ""),
            fullName = obj.optString("full_name", obj.optString("name", "")),
            avatar = obj.optString("avatar_url", ""),
            points = obj.optInt("points", obj.optInt("follower_count", 0)),
            faculty = obj.optString("faculty", ""),
            university = obj.optString("university", ""),
            level = obj.optString("academic_level", ""),
            streakDays = obj.optInt("daily_streak", 0),
            verificationBadge = badge
        )
    }

    private fun parseMarketItem(obj: JSONObject): MarketItem {
        val imagesArray = obj.optJSONArray("images")
        val imagesList = mutableListOf<String>()
        if (imagesArray != null) {
            for (i in 0 until imagesArray.length()) {
                val url = imagesArray.optString(i, "")
                if (url.isNotBlank()) imagesList.add(url)
            }
        } else {
            val img = obj.optString("image_url", "")
            if (img.isNotBlank()) imagesList.add(img)
        }

        val sellerIsVerified = obj.optBoolean("seller_is_verified", false) || obj.optString("verification_badge", "").equals("BLUE", ignoreCase = true) || obj.optString("verification_badge", "").equals("GOLD", ignoreCase = true)
        val badgeStr = obj.optString("verification_badge", "").uppercase(Locale.US)
        val badge = when (badgeStr) {
            "GOLD" -> VerificationBadge.GOLD
            "BLUE" -> VerificationBadge.BLUE
            else -> if (sellerIsVerified) VerificationBadge.BLUE else VerificationBadge.NONE
        }

        return MarketItem(
            id = obj.optString("id", ""),
            title = obj.optString("title", ""),
            price = obj.optLong("price", 0L),
            images = imagesList,
            sellerUsername = obj.optString("seller_username", ""),
            sellerAvatar = obj.optString("seller_avatar", ""),
            sellerName = obj.optString("seller_name", ""),
            sellerPhone = obj.optString("seller_phone", ""),
            sellerWhatsapp = obj.optString("seller_whatsapp", ""),
            sellerIsVerified = sellerIsVerified,
            verificationBadge = badge,
            sellerRating = obj.optDouble("seller_rating", 0.0),
            sellerReviewCount = obj.optInt("seller_review_count", 0),
            university = obj.optString("university", ""),
            location = obj.optString("location", ""),
            category = obj.optString("category", ""),
            condition = obj.optString("condition", ""),
            description = obj.optString("description", ""),
            postedTime = obj.optString("posted_time", obj.optString("time_ago", "Recently")),
            isFeatured = obj.optBoolean("is_featured", false),
            isSold = obj.optBoolean("is_sold", false)
        )
    }

"""

if idx_start != -1:
    content = content[:idx_start] + parsers + content[idx_start:]
    with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
        f.write(content)

