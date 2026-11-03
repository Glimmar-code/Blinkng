package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedVisibilityPolicyTest {
    @Test fun deletedPostDisappearsEvenWhenOriginalFeedSnapshotRetainsIt() {
        assertTrue(shouldHideFrozenFeedPost("post-1", "Author", "author", setOf("post-1"), emptySet()))
    }

    @Test fun unsuccessfulDeleteCanBeRolledBackWithoutReload() {
        assertFalse(shouldHideFrozenFeedPost("post-1", "Author", "author", emptySet(), emptySet()))
    }

    @Test fun muteMatchesStableUsernameNotUnrelatedDisplayName() {
        assertFalse(shouldHideFrozenFeedPost("p2", "Old Handle", "@new_handle", emptySet(), setOf("old handle")))
        assertTrue(shouldHideFrozenFeedPost("p2", "Old Handle", "@new_handle", emptySet(), setOf("new_handle")))
    }

    @Test fun legacyAuthorFallbackAppliesOnlyWithoutUsername() {
        assertTrue(shouldHideFrozenFeedPost("p2", "Muted Legacy", "", emptySet(), setOf("muted legacy")))
    }

    @Test fun unrelatedPostsRemainVisible() {
        assertFalse(shouldHideFrozenFeedPost("p3", "Friendly", "friend", setOf("p2"), setOf("muted")))
    }
}
