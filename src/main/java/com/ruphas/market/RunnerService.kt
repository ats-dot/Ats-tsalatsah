package com.ats.tsalatsah

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class RunnerService : Service() {

    companion object {
        @Volatile var running = false
        @Volatile var done = 0
        @Volatile var total = 0
        val logs = ArrayList<String>()
        private val fmt = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
        fun log(s: String) {
            synchronized(logs) {
                logs.add(0, fmt.format(java.util.Date()) + "  " + s)
                while (logs.size > 200) logs.removeAt(logs.size - 1)
            }
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private var gen = 0
    private var appCount = 0
    private var banner: View? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i == null) { stopSelf(); return START_NOT_STICKY }
        if (i.action == "STOP") { end("Dihentikan", false); return START_NOT_STICKY }
        val p = i.getStringArrayExtra("pkgs")
        if (p == null || p.isEmpty()) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("r", "Ats", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "r")
            .setContentTitle("Ats-tsalatsah")
            .setContentText("Membuka app satu per satu...")
            .setSmallIcon(android.R.drawable.ic_media_play).build())
        h.removeCallbacksAndMessages(null)
        hideBanner()
        val g = ++gen
        appCount = p.size
        running = true; done = 0; total = p.size * 2
        log("Mulai: ${p.size} app")
        step(g, p, i.getLongExtra("g1", 5000L), i.getLongExtra("g2", 7000L), i.getBooleanExtra("back", true), 0)
        return START_NOT_STICKY
    }

    private fun end(msg: String, back: Boolean) {
        gen++
        h.removeCallbacksAndMessages(null)
        running = false
        log(msg)
        val ok = msg == "Selesai"
        if (back) {
            val li = packageManager.getLaunchIntentForPackage(packageName)
            if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li) }
        }
        if (ok) showBanner()
        h.postDelayed({
            hideBanner()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, if (ok) 4000L else 0L)
    }

    private fun showBanner() {
        try {
            hideBanner()
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dp = resources.displayMetrics.density
            val tv = TextView(this)
            tv.text = "✓  Selesai · $appCount app dibuka"
            tv.setTextColor(Color.BLACK)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            tv.typeface = Typeface.DEFAULT_BOLD
            tv.setPadding((26 * dp).toInt(), (16 * dp).toInt(), (26 * dp).toInt(), (16 * dp).toInt())
            val bg = GradientDrawable()
            bg.setColor(Color.WHITE)
            bg.cornerRadius = 40 * dp
            bg.setStroke((1 * dp).toInt(), 0xFF999999.toInt())
            tv.background = bg
            tv.setOnClickListener { hideBanner() }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.CENTER
            lp.y = -(48 * dp).toInt()
            wm.addView(tv, lp)
            banner = tv
        } catch (e: Exception) {
            log("Tanda selesai gagal tampil")
        }
    }

    private fun hideBanner() {
        val b = banner ?: return
        banner = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(b)
        } catch (e: Exception) {
        }
    }

    private fun step(g: Int, p: Array<String>, g1: Long, g2: Long, back: Boolean, i: Int) {
        if (g != gen) return
        if (i >= p.size * 2) { end("Selesai", back); return }
        val pkg = p[i % p.size]
        val round = i / p.size + 1
        val li = packageManager.getLaunchIntentForPackage(pkg)
        if (li == null) {
            log("Gagal buka $pkg")
        } else {
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { startActivity(li) } catch (e: Exception) { log("Error $pkg") }
            log("Putaran $round: ${i % p.size + 1}/${p.size} $pkg")
        }
        done = i + 1
        h.postDelayed({ step(g, p, g1, g2, back, i + 1) }, if (li == null) 0L else if (round == 1) g1 else g2)
    }

    override fun onDestroy() { hideBanner(); running = false; super.onDestroy() }
}
