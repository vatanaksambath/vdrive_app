package com.khmerauto.nav.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import com.khmerauto.nav.map.routing.NavigationRouteEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SecondaryHudManager"

/**
 * Secondary Cluster & Windscreen HUD Manager for Geely Galaxy M7 (Flyme Auto / ECARX).
 *
 * Responsibilities:
 * 1. Automatically detects secondary instrument cluster and HUD displays via Android DisplayManager.
 * 2. Launches ClusterMapPresentation on discovered secondary displays.
 * 3. Provides an in-app simulated HUD preview overlay for single-screen tablet testing.
 */
class SecondaryClusterHudManager(
    private val context: Context,
    val routeEngine: NavigationRouteEngine
) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private var activePresentation: ClusterMapPresentation? = null

    private val _isSimulatedHudVisible = MutableStateFlow(false)
    val isSimulatedHudVisible: StateFlow<Boolean> = _isSimulatedHudVisible.asStateFlow()

    private val _isSecondaryDisplayConnected = MutableStateFlow(false)
    val isSecondaryDisplayConnected: StateFlow<Boolean> = _isSecondaryDisplayConnected.asStateFlow()

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.i(TAG, "Secondary display added: ID=$displayId. Updating HUD projection...")
            checkForSecondaryDisplays()
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.i(TAG, "Secondary display removed: ID=$displayId.")
            checkForSecondaryDisplays()
        }

        override fun onDisplayChanged(displayId: Int) {
            checkForSecondaryDisplays()
        }
    }

    fun start() {
        Log.i(TAG, "Starting SecondaryClusterHudManager...")
        displayManager.registerDisplayListener(displayListener, null)
        checkForSecondaryDisplays()
    }

    fun stop() {
        Log.i(TAG, "Stopping SecondaryClusterHudManager...")
        displayManager.unregisterDisplayListener(displayListener)
        dismissPresentation()
    }

    fun toggleSimulatedHud() {
        _isSimulatedHudVisible.value = !_isSimulatedHudVisible.value
    }

    fun setSimulatedHudVisible(visible: Boolean) {
        _isSimulatedHudVisible.value = visible
    }

    private fun checkForSecondaryDisplays() {
        val displays = displayManager.displays
        val secondary = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

        if (secondary != null) {
            Log.i(TAG, "Secondary automotive display detected: ID=${secondary.displayId}, Name=${secondary.name}, Size=${secondary.width}x${secondary.height}")
            _isSecondaryDisplayConnected.value = true
            showPresentation(secondary)
        } else {
            Log.d(TAG, "No physical secondary display connected. Total displays: ${displays.size}")
            _isSecondaryDisplayConnected.value = false
            dismissPresentation()
        }
    }

    private fun showPresentation(display: Display) {
        if (activePresentation != null && activePresentation?.display?.displayId == display.displayId) {
            return
        }

        dismissPresentation()

        try {
            val presentation = ClusterMapPresentation(context, display, routeEngine)
            presentation.show()
            activePresentation = presentation
            Log.i(TAG, "ClusterMapPresentation successfully projected onto display ID ${display.displayId}!")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to project presentation on secondary display: ${e.message}")
        }
    }

    private fun dismissPresentation() {
        try {
            activePresentation?.dismiss()
        } catch (_: Exception) {}
        activePresentation = null
    }
}
