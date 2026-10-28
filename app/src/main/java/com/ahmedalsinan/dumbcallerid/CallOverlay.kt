package com.ahmedalsinan.dumbcallerid

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

// Floating card drawn over other apps (including the incoming call screen).
// While it is visible, Android treats the process as perceptible, so it is not killed.
// The held view is inflated from the application context, which lives as long as the process.
@SuppressLint("StaticFieldLeak")
object CallOverlay {
    private var view: View? = null
    private var number: String = ""

    // Where the user last dragged the card, so the next call shows it in the same place. Saved as a share of
    // screen height ("top_fraction"), not pixels, so it stays on screen after rotating or a display-size change.
    private const val PREFS = "overlay"
    private const val KEY_TOP = "top_fraction"

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    // Returns false if Android refused the window, e.g. when the popup permission was revoked just before the call.
    fun show(context: Context, number: String): Boolean {
        this.number = number

        view?.let {
            if (it.isAttachedToWindow) {
                it.findViewById<TextView>(R.id.overlayNumber).text = PhoneNumbers.format(context, number)
                return true
            }
            view = null // removed by the system; add a fresh one
        }

        val appContext = context.applicationContext
        val windowManager = appContext.getSystemService(WindowManager::class.java)
        // No parent: the window manager supplies the layout params.
        @SuppressLint("InflateParams")
        val overlay = LayoutInflater.from(appContext).inflate(R.layout.overlay_call, null)
        overlay.findViewById<TextView>(R.id.overlayNumber).text = PhoneNumbers.format(context, number)
        val screen = appContext.resources.displayMetrics
        overlay.measure(
            View.MeasureSpec.makeMeasureSpec(screen.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // SHOW_WHEN_LOCKED: while ringing on a locked phone, the call screen covers the lock screen and
            // overlays are hidden unless they opt in. Deprecated for activities, still honoured for windows.
            @Suppress("DEPRECATION")
            (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            val saved = prefs(appContext).getFloat(KEY_TOP, -1f).takeIf { it >= 0f }
            y = popupTop(saved, screen.heightPixels, overlay.measuredHeight)
        }

        overlay.findViewById<View>(R.id.overlayLookup).setOnClickListener {
            val locked = appContext.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if (locked) appContext.startActivity(UnlockActivity.intent(appContext, this.number))
            else Telegram.lookUp(appContext, this.number)
            dismiss()
        }
        overlay.findViewById<View>(R.id.overlayClose).setOnClickListener { dismiss() }
        enableVerticalDrag(overlay, params)

        try {
            windowManager.addView(overlay, params)
        } catch (e: RuntimeException) { // BadTokenException, InvalidDisplayException, SecurityException
            Log.w(LOG_TAG, "Popup window refused", e)
            return false
        }
        view = overlay
        return true
    }

    fun dismiss() {
        val overlay = view ?: return
        view = null
        // The system may already have removed the window (e.g. overlay permission revoked).
        if (overlay.isAttachedToWindow) {
            overlay.context.getSystemService(WindowManager::class.java).removeView(overlay)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Drag the card up or down; taps still reach the buttons. Kept on screen, remembered on release.
    @SuppressLint("ClickableViewAccessibility")
    private fun enableVerticalDrag(overlay: View, params: WindowManager.LayoutParams) {
        val windowManager = overlay.context.getSystemService(WindowManager::class.java)
        val slop = ViewConfiguration.get(overlay.context).scaledTouchSlop
        var downY = 0f
        var startY = 0
        var dragging = false

        overlay.setOnTouchListener { _, event ->
            val dy = event.rawY - downY
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(dy) > slop) dragging = true
                    if (dragging && overlay.isAttachedToWindow) {
                        val screenHeight = overlay.resources.displayMetrics.heightPixels
                        params.y = clampTop(startY + dy.toInt(), screenHeight, overlay.height)
                        windowManager.updateViewLayout(overlay, params)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val fraction = topFraction(params.y, overlay.resources.displayMetrics.heightPixels)
                        prefs(overlay.context).edit().putFloat(KEY_TOP, fraction).apply()
                    }
                }
            }
            true
        }
    }
}

// Default a quarter down: clear of the answer/decline banner some phones (e.g. Motorola) show at the top,
// and of the answer/decline buttons at the bottom of full-screen call screens.
internal const val DEFAULT_TOP_FRACTION = 0.25f

// The card's top edge in pixels, from the saved share of screen height (null: never dragged), kept fully on screen.
internal fun popupTop(savedFraction: Float?, screenHeight: Int, cardHeight: Int): Int =
    clampTop(((savedFraction ?: DEFAULT_TOP_FRACTION) * screenHeight).toInt(), screenHeight, cardHeight)

internal fun clampTop(top: Int, screenHeight: Int, cardHeight: Int): Int = top.coerceIn(0, maxOf(0, screenHeight - cardHeight))

internal fun topFraction(top: Int, screenHeight: Int): Float =
    if (screenHeight <= 0) DEFAULT_TOP_FRACTION else (top.toFloat() / screenHeight).coerceIn(0f, 1f)
