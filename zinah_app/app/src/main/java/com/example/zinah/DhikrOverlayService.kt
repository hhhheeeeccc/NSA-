package com.example.zinah

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.view.MotionEvent
import android.widget.FrameLayout
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import kotlin.math.abs

class DhikrOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val dhikrText = intent?.getStringExtra("dhikr_text") ?: "صلي على محمد"
        showOverlay(dhikrText)
        
        // Auto-remove after 8 seconds
        handler.postDelayed({
            stopSelf()
        }, 8000)
        
        return START_NOT_STICKY
    }

    private fun showOverlay(text: String) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END // Side of screen (Right side)
            x = 0
            y = 0
        }

        // Create the actual view content programmatically
        val container = FrameLayout(this).apply {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#E02E7D32")) // Dark green with alpha
                cornerRadius = 50f
                setStroke(2, Color.parseColor("#FFD4AF37")) // Gold border
            }
            background = shape
            setPadding(20, 10, 20, 10)
        }

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val textView = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(10, 0, 20, 0)
        }

        val icon = ImageView(this).apply {
            // Using a simple system icon for now, user can replace with app logo
            setImageResource(android.R.drawable.ic_dialog_info) 
            layoutParams = android.widget.LinearLayout.LayoutParams(80, 80)
        }
        
        layout.addView(icon)
        layout.addView(textView)
        container.addView(layout)
        
        floatingView = container

        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0.toFloat()
            private var initialTouchY: Float = 0.toFloat()

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        if (diffX < 10 && diffY < 10) {
                            // Clicked!
                            stopSelf()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Move on Y axis only for "side logo" feel, or both
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            // Handle cases where permission might have been revoked
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
