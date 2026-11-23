package com.example.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.Display
import android.view.Window
import android.view.WindowManager

/**
 * Configures the Activity window to take full advantage of high-refresh-rate displays
 * (60Hz / 90Hz / 120Hz / 144Hz+) when supported by the hardware while respecting
 * system battery-saver settings and Android Adaptive Refresh Rate (ARR).
 */
object DisplayRefreshRateOptimizer {

    data class DisplayModeCandidate(
        val modeId: Int,
        val physicalWidth: Int,
        val physicalHeight: Int,
        val refreshRate: Float
    )

    /**
     * Applies the optimal supported display refresh rate mode matching the active display's
     * physical resolution, or resets to the system default mode when Battery Saver is active.
     */
    fun applyOptimalRefreshRate(activity: Activity) {
        val window: Window = activity.window ?: return
        val isPowerSave = isPowerSaveMode(activity)

        // Enable Android 15+ Adaptive Refresh Rate power-savings balancing cleanly when available.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            runCatching {
                window.isFrameRatePowerSavingsBalanced = true
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        val display: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager?.defaultDisplay
        } ?: return

        val activeMode = display.mode ?: return
        val supported = display.supportedModes ?: return
        val candidates = supported.map { mode ->
            DisplayModeCandidate(
                modeId = mode.modeId,
                physicalWidth = mode.physicalWidth,
                physicalHeight = mode.physicalHeight,
                refreshRate = mode.refreshRate
            )
        }

        val targetModeId = selectBestDisplayModeId(
            currentModeId = activeMode.modeId,
            currentWidth = activeMode.physicalWidth,
            currentHeight = activeMode.physicalHeight,
            supportedModes = candidates,
            isPowerSaveMode = isPowerSave
        )

        val currentAttr = window.attributes
        if (currentAttr.preferredDisplayModeId != targetModeId) {
            val updated = WindowManager.LayoutParams().apply {
                copyFrom(currentAttr)
                preferredDisplayModeId = targetModeId
            }
            window.attributes = updated
        }
    }

    /**
     * Pure deterministic mode selector usable in unit tests:
     * - In Power Save Mode: returns 0 (default system power-saving mode).
     * - Otherwise: selects the supported mode with exact same native physical resolution
     *   and highest refresh rate (>= 60Hz). Never picks an invalid or lower-res mode.
     */
    internal fun selectBestDisplayModeId(
        currentModeId: Int,
        currentWidth: Int,
        currentHeight: Int,
        supportedModes: List<DisplayModeCandidate>,
        isPowerSaveMode: Boolean
    ): Int {
        if (isPowerSaveMode || supportedModes.isEmpty()) {
            return 0
        }

        val sameResolution = supportedModes.filter {
            it.physicalWidth == currentWidth &&
                it.physicalHeight == currentHeight &&
                it.refreshRate >= 59.5f &&
                it.refreshRate.isFinite()
        }
        if (sameResolution.isEmpty()) {
            return currentModeId
        }

        val highest = sameResolution.maxWithOrNull(
            compareBy<DisplayModeCandidate> { it.refreshRate }
                .thenBy { if (it.modeId == currentModeId) 1 else 0 }
        ) ?: return currentModeId

        return highest.modeId
    }

    internal fun isPowerSaveMode(context: Context): Boolean {
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode == true
        }.getOrDefault(false)
    }
}
