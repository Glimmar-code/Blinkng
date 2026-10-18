package com.example.call

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CallNotificationLedgerTest {
    private lateinit var context: Context
    private val callId = "4a4627fb-14dd-4c8f-a379-d5c41f0561b2"

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("blink_call_notification_ledger", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun `incoming invite requires exact recipient and caller identities`() {
        assertTrue(CallNotificationLedger.validRecipient("callee", "callee", "caller", "callee", true))
        assertFalse(CallNotificationLedger.validRecipient("callee", null, "caller", "callee", true))
        assertFalse(CallNotificationLedger.validRecipient("callee", "other", "caller", "callee", true))
        assertFalse(CallNotificationLedger.validRecipient("callee", "callee", "callee", "callee", true))
        assertFalse(CallNotificationLedger.validRecipient("caller", "caller", "caller", "callee", true))
        assertTrue(CallNotificationLedger.validRecipient("caller", "caller", "caller", "callee", false))
    }

    @Test fun `expired invitations cannot ring`() {
        assertFalse(CallNotificationLedger.isFreshInvite(
            Instant.ofEpochMilli(1_000L).toString(), 2_000L
        ))
        assertTrue(CallNotificationLedger.isFreshInvite(
            Instant.ofEpochMilli(3_000L).toString(), 2_000L
        ))
        assertFalse(CallNotificationLedger.isFreshInvite("invalid", 2_000L))
    }

    @Test fun `repeated FCM invites only ring once per account`() {
        assertTrue(CallNotificationLedger.claimInvite(context, "callee", callId, null))
        assertFalse(CallNotificationLedger.claimInvite(context, "callee", callId, null))
        assertTrue(CallNotificationLedger.claimInvite(context, "another", callId, null))
    }

    @Test fun `late invite is ignored after cancellation`() {
        CallNotificationLedger.markUpdated(context, "callee", callId, "cancelled")
        assertFalse(CallNotificationLedger.claimInvite(context, "callee", callId, null))
        assertTrue(CallNotificationLedger.claimInvite(context, "other", callId, null))
    }

    @Test fun `answered on another device suppresses subsequent invites`() {
        CallNotificationLedger.markUpdated(context, "callee", callId, "answered")
        assertFalse(CallNotificationLedger.claimInvite(context, "callee", callId, null))
    }

    @Test fun `invalid call identifiers cannot poison persisted state`() {
        assertFalse(CallNotificationLedger.claimInvite(context, "callee", "not-a-call", null))
        CallNotificationLedger.markUpdated(context, "callee", "not-a-call", "cancelled")
        assertTrue(CallNotificationLedger.claimInvite(context, "callee", callId, null))
    }
}
