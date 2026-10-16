package com.focusguard.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

/**
 * The blocking engine.
 *
 * Android has no public "block this app" API, so the standard approach is used:
 * an AccessibilityService watches window-change events, and whenever the app that
 * just came to the foreground is blocked by the user's config, the service
 * immediately sends the user back to the home screen and shows an overlay card
 * ("App is blocked") if the draw-over-other-apps permission was granted.
 */
class BlockAccessibilityService : AccessibilityService() {

    companion object {
        private const val OVERLAY_MS = 2_000L

        /** Whether Android has this service enabled in Accessibility settings. */
        fun isAccessibilityEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val fullyQualified = "${context.packageName}/${BlockAccessibilityService::class.java.name}"
            val shortened = "${context.packageName}/.${BlockAccessibilityService::class.java.simpleName}"
            return enabledServices.split(':').any { it == fullyQualified || it == shortened }
        }
    }

    @Volatile
    private var cachedConfig: BlockConfig? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlay: FrameLayout? = null
    private var hideOverlayRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Keep a live copy of the config so evaluations never touch disk per event.
        scope.launch {
            blockDataStore.data.collect { prefs ->
                cachedConfig = BlockStore.decode(prefs[BlockStore.KEY])
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        mainHandler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Ignore our own windows and system UI chrome.
        if (pkg == packageName || pkg == "com.android.systemui") return

        val config = cachedConfig ?: runBlocking { BlockStore.current(blockDataStore) }
        val isEligible = runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            isEligibleUserApp(
                isSystemApp = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
            )
        }.getOrDefault(false)
        if (!config.isAppBlocked(pkg, LocalDateTime.now(), isEligibleUserApp = isEligible)) return

        // Never debounce enforcement: a repeated launch attempt must also be sent Home.
        runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
        if (Settings.canDrawOverlays(this)) showOverlay(labelFor(pkg))
    }

    private fun labelFor(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun showOverlay(appLabel: String) {
        try {
            hideOverlay() // replace any currently visible card

            val density = resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(28), dp(20), dp(28), dp(20))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(0xE6202020.toInt())
                }
                addView(TextView(context).apply {
                    text = "Blocked"
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                })
                addView(TextView(context).apply {
                    text = "$appLabel is blocked right now.\nYou can open it outside blocked hours."
                    setTextColor(0xB3FFFFFF.toInt())
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, 0)
                })
            }

            val container = FrameLayout(this).apply {
                addView(
                    card,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
            }

            val params = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT
            ).apply {
                dimAmount = 0.30f
                gravity = Gravity.CENTER
            }

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(container, params)
            overlay = container

            val hide = Runnable { hideOverlay() }
            hideOverlayRunnable = hide
            mainHandler.postDelayed(hide, OVERLAY_MS)
        } catch (_: Exception) {
            // Overlay permission can lapse or the view may already be attached — never crash here.
        }
    }

    private fun hideOverlay() {
        hideOverlayRunnable?.let(mainHandler::removeCallbacks)
        hideOverlayRunnable = null
        val current = overlay ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(current)
        } catch (_: Exception) {
        }
        overlay = null
    }
}
