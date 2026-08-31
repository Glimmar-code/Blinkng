package com.example

import android.content.Context
import com.example.viewmodel.BlinkViewModel

/** Compatibility shim for older MainActivity wiring; never generates fake notifications. */
fun BlinkViewModel.simulateBackgroundNotification(context: Context) {
    showToast("Test notifications are disabled. Real notifications are used.")
}
