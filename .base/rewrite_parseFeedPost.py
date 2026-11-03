import re
with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

new_parseFeedPost = """    private fun parseFeedPost(obj: JSONObject): FeedPost {
        val author = obj.optString("author", obj.optString("username", ""))
        val imagesArray = obj.optJSONArray("images")
        val imagesList = mutableListOf<String>()
        if (imagesArray != null) {
            for (i in 0 until imagesArray.length()) {
                val url = imagesArray.optString(i, "")
                if (url.isNotBlank()) imagesList.add(url)
            }
        }
        val imageUrl = obj.optString("image_url", obj.optString("media_url", ""))
        if (imageUrl.isNotBlank() && !imagesList.contains(imageUrl)) {
            imagesList.add(imageUrl)
        }

        val tagsArray = obj.optJSONArray("tags")
        val tagsList = mutableListOf<String>()
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                val t = tagsArray.optString(i, "")
                if (t.isNotBlank()) tagsList.add(t)
            }
        }

        val mentionsArray = obj.optJSONArray("mentions")
        val mentionsList = mutableListOf<String>()
        if (mentionsArray != null) {
            for (i in 0 until mentionsArray.length()) {
                val m = mentionsArray.optString(i, "")
                if (m.isNotBlank()) mentionsList.add(m)
            }
        }

        var poll: PostPoll? = null
        val pollObj = obj.optJSONObject("poll_data")
        if (pollObj != null) {
            val q = pollObj.optString("question", "")
            val optsArr = pollObj.optJSONArray("options")
            val opts = mutableListOf<PollOption>()
            if (optsArr != null) {
                for (i in 0 until optsArr.length()) {
                    val opt = optsArr.optJSONObject(i)
                    if (opt != null) {
                        opts.add(PollOption(
                            id = opt.optString("id", ""),
                            text = opt.optString("text", ""),
                            votes = opt.optInt("votes", 0),
                            isVotedByMe = opt.optBoolean("is_voted_by_me", false)
                        ))
                    }
                }
            }
            poll = PostPoll(
                question = q,
                options = opts,
                totalVotes = pollObj.optInt("total_votes", 0),
                hasVoted = pollObj.optBoolean("has_voted", false)
            )
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
            authorAvatar = obj.optString("author_avatar", obj.optString("avatar_url", "")),
            facultyTag = obj.optString("faculty_tag", obj.optString("faculty", "")),
            isVerified = isVerified,
            verificationBadge = badge,
            timeAgo = obj.optString("time_ago", "Recently"),
            text = obj.optString("text", obj.optString("content", "")),
            images = imagesList,
            tags = tagsList,
            mentions = mentionsList,
            poll = poll,
            likes = obj.optInt("likes_count", obj.optInt("like_count", obj.optInt("likes", 0))),
            isLiked = obj.optBoolean("is_liked", false),
            commentsCount = obj.optInt("comments_count", obj.optInt("comment_count", 0)),
            sharesCount = obj.optInt("shares_count", obj.optInt("share_count", 0)),
            viewsCount = obj.optInt("views_count", obj.optInt("view_count", 0)),
            isBookmarked = obj.optBoolean("is_bookmarked", false),
            isReel = obj.optBoolean("is_reel", false),
            videoDuration = obj.optString("video_duration", "0:00"),
            videoUrl = obj.optString("video_url", null),
            audience = obj.optString("audience", "Everyone"),
            category = obj.optString("category", "Campus Life"),
            location = obj.optString("location", null).takeIf { it?.isNotBlank() == true },
            linkUrl = obj.optString("link_url", null).takeIf { it?.isNotBlank() == true },
            allowComments = obj.optBoolean("allow_comments", true),
            hideLikes = obj.optBoolean("hide_likes", false),
            isPinned = obj.optBoolean("is_pinned", false),
            isDisappearing = obj.optBoolean("is_disappearing", false),
            audioTitle = obj.optString("audio_title", null).takeIf { it?.isNotBlank() == true },
            altText = obj.optString("alt_text", null).takeIf { it?.isNotBlank() == true }
        )
    }"""

pattern = r'    private fun parseFeedPost\(obj: JSONObject\): FeedPost \{[\s\S]*?isDisappearing = obj\.optBoolean\("is_disappearing", false\)\n\s*\)\n\s*\}'
content = re.sub(pattern, new_parseFeedPost, content)

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)
