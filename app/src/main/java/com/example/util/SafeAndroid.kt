package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast

/**
 * Reads preferences defensively across app upgrades.
 *
 * Android SharedPreferences throws ClassCastException when a key was written with a
 * different primitive type by an older build. These helpers recover the stored value
 * where possible instead of letting a screen crash on open.
 */
fun SharedPreferences.safeString(key: String, defaultValue: String? = null): String? =
    runCatching { getString(key, defaultValue) }.getOrElse {
        when (val value = all[key]) {
            null -> defaultValue
            is String -> value
            else -> value.toString()
        }
    }

fun SharedPreferences.safeInt(key: String, defaultValue: Int = 0): Int =
    runCatching { getInt(key, defaultValue) }.getOrElse {
        when (val value = all[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: defaultValue
            is Boolean -> if (value) 1 else 0
            else -> defaultValue
        }
    }

fun SharedPreferences.safeLong(key: String, defaultValue: Long = 0L): Long =
    runCatching { getLong(key, defaultValue) }.getOrElse {
        when (val value = all[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: defaultValue
            is Boolean -> if (value) 1L else 0L
            else -> defaultValue
        }
    }

fun SharedPreferences.safeBoolean(key: String, defaultValue: Boolean = false): Boolean =
    runCatching { getBoolean(key, defaultValue) }.getOrElse {
        when (val value = all[key]) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> defaultValue
            }
            else -> defaultValue
        }
    }

/**
 * Starts Android activities without allowing ActivityNotFoundException or OEM intent
 * failures to terminate Blink. Non-Activity contexts automatically receive NEW_TASK.
 */
fun Context.startActivitySafely(
    intent: Intent,
    failureMessage: String = "Unable to open this screen."
): Boolean {
    val launchIntent = Intent(intent)
    if (this !is Activity) launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    return runCatching {
        startActivity(launchIntent)
        true
    }.getOrElse {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
        false
    }
}
