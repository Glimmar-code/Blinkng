package com.example.sharing

import android.content.Context
import android.net.Uri
import com.blinkng.shared.BlinkContestShare

/** Link target survives sign-in and onboarding without casting an automatic vote. */
data class ContestVoteTarget(val contestId: String, val entryId: String)

object ContestVoteRoute {
    private const val PREFS = "blink_contest_pending_vote"
    fun parse(uri: Uri?): ContestVoteTarget? {
        if (uri == null) return null
        val paths = uri.pathSegments.filter { it.isNotBlank() }
        val fromWeb = uri.scheme == "https" &&
            uri.host?.lowercase() in setOf("blink.com.ng", "www.blink.com.ng") &&
            paths.size == 3 && paths[0] == "contest"
        val fromApp = uri.scheme == "blink" && uri.host == "contest" &&
            paths.size == 2
        if (!fromWeb && !fromApp) return null
        val id = if (fromWeb) paths[1] else paths[0]
        val entry = if (fromWeb) paths[2] else paths[1]
        if (!BlinkContestShare.isValid(id, entry)) return null
        return ContestVoteTarget(id.lowercase(), entry.lowercase())
    }

    fun store(context: Context, target: ContestVoteTarget) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("contest", target.contestId)
            .putString("entry", target.entryId).apply()
    }

    fun pending(context: Context): ContestVoteTarget? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val contest = prefs.getString("contest", null).orEmpty()
        val entry = prefs.getString("entry", null).orEmpty()
        return if (BlinkContestShare.isValid(contest, entry)) ContestVoteTarget(contest, entry) else null
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
