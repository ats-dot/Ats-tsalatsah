package com.ats.tsalatsah

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class RunnerService : Service() {

    companion object {
        @Volatile var running = false
        @Volatile var done = 0
        @Volatile var total = 0
        val logs = ArrayList<String>()
        fun log(s: String) {
            synchronized(logs) {
                logs.add(0, s)
                while (logs.size > 100) logs.removeAt(logs.size - 1)
            }
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private var gen = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i == null) { stopSelf(); return START_NOT_STICKY }
        if (i.action == "STOP") { end("Dihentikan", false); return START_NOT_STICKY }
        val p = i.getStringArrayExtra("pkgs")
        if (p == null || p.isEmpty()) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("r", "Ats-tsalatsah", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "r")
            .setContentTitle("Ats-tsalatsah")
            .setContentText("Membuka app satu per satu...")
            .setSmallIcon(android.R.drawable.ic_media_play).build())
        h.removeCallbacksAndMessages(null)
        val g = ++gen
        running = true; done = 0; total = p.size * 2
        log("Mulai: ${p.size} app")
        step(g, p, i.getLongExtra("g1", 10000L), i.getLongExtra("g2", 30000L), i.getBooleanExtra("back", true), 0)
        return START_NOT_STICKY
    }

    private fun end(msg: String, back: Boolean) {
        gen++
        h.removeCallbacksAndMessages(null)
        running = false
        log(msg)
        if (back) {
            val li = packageManager.getLaunchIntentForPackage(packageName)
            if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li) }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        h.postDelayed({ step(g, p, g1, g2, back, i + 1) }, if (round == 1) g1 else g2)
    }

    override fun onDestroy() { running = false; super.onDestroy() }
}
