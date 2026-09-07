package com.example.call

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CallSoundPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("blink_call_sound_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `voice and video ringing can be controlled independently`() {
        assertTrue(CallSoundPreferences.ringEnabled(context, CallType.AUDIO))
        assertTrue(CallSoundPreferences.ringEnabled(context, CallType.VIDEO))

        CallSoundPreferences.setRingEnabled(context, CallType.AUDIO, false)

        assertFalse(CallSoundPreferences.ringEnabled(context, CallType.AUDIO))
        assertTrue(CallSoundPreferences.ringEnabled(context, CallType.VIDEO))
        assertEquals(null, CallSoundPreferences.ringtoneUri(context, CallType.AUDIO))
    }

    @Test
    fun `voice and video ringtone selections are isolated`() {
        val voice = Uri.parse("content://blink.test/ringtone/voice")
        val video = Uri.parse("content://blink.test/ringtone/video")

        CallSoundPreferences.setRingtoneUri(context, CallType.AUDIO, voice)
        CallSoundPreferences.setRingtoneUri(context, CallType.VIDEO, video)

        assertEquals(voice, CallSoundPreferences.ringtoneUri(context, CallType.AUDIO))
        assertEquals(video, CallSoundPreferences.ringtoneUri(context, CallType.VIDEO))
    }

    @Test
    fun `changing call sound settings versions the Android notification channel`() {
        val before = CallSoundPreferences.channelId(context, CallType.AUDIO)

        CallSoundPreferences.setVibrateEnabled(context, false)
        val afterVibrationChange = CallSoundPreferences.channelId(context, CallType.AUDIO)

        CallSoundPreferences.setRingEnabled(context, CallType.AUDIO, false)
        val afterSilentChange = CallSoundPreferences.channelId(context, CallType.AUDIO)

        assertNotEquals(before, afterVibrationChange)
        assertNotEquals(afterVibrationChange, afterSilentChange)
        assertTrue(afterSilentChange.startsWith("blink_incoming_voice_"))
    }

    @Test
    fun `video channel id is distinct from voice channel id`() {
        assertNotEquals(
            CallSoundPreferences.channelId(context, CallType.AUDIO),
            CallSoundPreferences.channelId(context, CallType.VIDEO)
        )
    }
}
