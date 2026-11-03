package com.example.ui.screens

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NewMemberPerksStoreTest {
    @Test fun guideShowsOncePerAccountAndNeverForUnknownAccount() {
        val context = RuntimeEnvironment.getApplication()
        val accountA = "first-student-onboarding-test"
        val accountB = "second-student-onboarding-test"
        context.getSharedPreferences("blink_new_member_perks_v1", 0).edit().clear().commit()

        assertFalse(NewMemberPerksStore.shouldShow(context, ""))
        assertTrue(NewMemberPerksStore.shouldShow(context, accountA))
        NewMemberPerksStore.markSeen(context, accountA)
        assertFalse(NewMemberPerksStore.shouldShow(context, accountA))
        assertTrue(NewMemberPerksStore.shouldShow(context, accountB))
    }
}
