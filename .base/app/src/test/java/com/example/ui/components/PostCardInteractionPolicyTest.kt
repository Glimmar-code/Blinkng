package com.example.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCardInteractionPolicyTest {
    @Test
    fun doubleTapLikesAnUnlikedPost() {
        assertTrue(shouldApplyPostDoubleTapLike(alreadyLiked = false))
    }

    @Test
    fun doubleTapNeverUnlikesAlreadyLikedPost() {
        assertFalse(shouldApplyPostDoubleTapLike(alreadyLiked = true))
    }
}
