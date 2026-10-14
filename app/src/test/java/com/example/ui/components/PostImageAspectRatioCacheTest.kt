package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostImageAspectRatioCacheTest {
    @Test
    fun remembersAspectRatioWithoutRetainingImageObjects() {
        val url = "aspect-test://one"
        PostImageAspectRatioCache.put(url, 1.25f)
        assertEquals(1.25f, PostImageAspectRatioCache.get(url)!!, 0.0001f)
    }

    @Test
    fun clampsExtremeRatiosAndIgnoresInvalidValues() {
        val url = "aspect-test://limits"
        PostImageAspectRatioCache.put(url, 99f)
        assertEquals(1.91f, PostImageAspectRatioCache.get(url)!!, 0.0001f)
        PostImageAspectRatioCache.put(url, Float.NaN)
        assertEquals(1.91f, PostImageAspectRatioCache.get(url)!!, 0.0001f)
        PostImageAspectRatioCache.put("", 0.5f)
        assertNull(PostImageAspectRatioCache.get(""))
    }

    @Test
    fun retainsTallPortraitImageRatio() {
        val url = "aspect-test://portrait"
        PostImageAspectRatioCache.put(url, 0.51f)
        assertEquals(0.51f, PostImageAspectRatioCache.get(url)!!, 0.0001f)
        PostImageAspectRatioCache.put(url, 0.1f)
        assertEquals(0.50f, PostImageAspectRatioCache.get(url)!!, 0.0001f)
    }

    @Test
    fun evictsOldRatiosToBoundMemory() {
        val first = "aspect-test://eviction-first"
        PostImageAspectRatioCache.put(first, 0.9f)
        repeat(140) { i ->
            PostImageAspectRatioCache.put("aspect-test://eviction-${i}", 1f)
        }
        assertNull(PostImageAspectRatioCache.get(first))
    }
}
