package com.example.zinah

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Side overlay notification service — shows a beautiful floating card on the
 * right edge of the screen with the dhikr text.
 *
 * Design:
 *  - Vertical card (icon on top, title below, dhikr text below that)
 *  - Deep emerald gradient background
 *  - Gold border + gold glow
 *  - Crescent icon (uses ic_notification drawable)
 *  - Drop shadow for depth
 *  - Rounded corners (24dp)
 *  - Auto-dismisses after 10 seconds
 *  - Draggable on Y axis; tap to dismiss
 */
class DhikrOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val dhikrText = intent?.getStringExtra("dhikr_text") ?: "صلي على محمد"
        showOverlay(dhikrText)

        // Auto-remove after 10 seconds
        handler.postDelayed({
            stopSelf()
        }, 10_000)

        return START_NOT_STICKY
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private fun showOverlay(text: String) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END // Right side of screen (unchanged)
            x = dpToPx(8)
            y = 0
        }

        // ===== Main container — vertical card with rounded corners + shadow =====
        val container = FrameLayout(this).apply {
            // Background: deep emerald with gold border, large rounded corners
            val shape = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(
                    Color.parseColor("#FF1B5E20"), // top: deep emerald
                    Color.parseColor("#FF0B3D20")  // bottom: darker emerald
                )
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#FFD4AF37")) // gold border
            }
            background = shape
            // Padding for content inside the card
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            // Elevation for shadow (API 21+)
            elevation = dpToPx(12).toFloat()
        }

        // ===== Vertical layout: icon → title → dhikr text =====
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // ===== Crescent icon (gold circle background + notification icon) =====
        val iconContainer = FrameLayout(this).apply {
            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFD700")) // semi-transparent gold
                setStroke(dpToPx(1), Color.parseColor("#66FFD700"))
            }
            background = iconBg
            val size = dpToPx(56)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = dpToPx(12)
            }
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification)
            // Tint gold
            setColorFilter(Color.parseColor("#FFFFD700"))
            val iconSize = dpToPx(32)
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        iconContainer.addView(icon)

        // ===== Title: "تطبيق زينة" (small, gold) =====
        val titleView = TextView(this).apply {
            this.text = "تطبيق زينة"
            setTextColor(Color.parseColor("#FFFFD700"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpToPx(6))
            gravity = Gravity.CENTER
        }

        // ===== Gold divider line =====
        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#55FFD700"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(1)).apply {
                bottomMargin = dpToPx(10)
            }
        }

        // ===== Dhikr text (white, larger) =====
        val textView = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            // Limit width so long adhkar wrap nicely
            maxWidth = dpToPx(220)
            setLineSpacing(dpToPx(2).toFloat(), 1f)
        }

        // Build the layout
        layout.addView(iconContainer)
        layout.addView(titleView)
        layout.addView(divider)
        layout.addView(textView)
        container.addView(layout)

        floatingView = container

        // ===== Touch listener: drag on Y axis, tap to dismiss =====
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialY: Int = 0
            private var initialTouchY: Float = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = params.y
                        initialTouchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            // Tap — dismiss
                            stopSelf()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - initialTouchY
                        if (abs(dy) > 10) {
                            moved = true
                            params.y = initialY + dy.toInt()
                            try {
                                windowManager?.updateViewLayout(floatingView, params)
                            } catch (e: Exception) {}
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            // Permission might have been revoked
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {}
        }
        handler.removeCallbacksAndMessages(null)
    }
}
