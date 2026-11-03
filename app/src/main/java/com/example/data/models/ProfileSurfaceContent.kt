package com.example.data.models

data class ProfileSurfaceContent(
    val posts: List<FeedPost>,
    val likedPosts: List<FeedPost> = emptyList(),
    val savedPosts: List<FeedPost> = emptyList()
)
