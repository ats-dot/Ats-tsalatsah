package com.ruphas.market

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class RunnerService : AccessibilityService() {

    companion object {
        @Volatile var instance: RunnerService? = null
        val logs = ArrayList<String>()
        var onChange: (() -> Unit)? = null
        fun log(s: String) {
            Handler(Looper.getMainLooper()).post {
                logs.add(0, s)
                if (logs.size > 200) logs.removeAt(logs.size - 1)
                onChange?.invoke()
            }
        }
    }

    private class Step(val round: Int, val pkg: String, val label: String, val delay: Long, val idx: Int, val n: Int)

    private val h = Handler(Looper.getMainLooper())
    private var running = false
    private var gen = 0

    override fun onServiceConnected() { instance = this; log("Layanan aktif") }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { stop(); instance = null; super.onDestroy() }

    fun isRunning() = running

    fun start(apps: List<Pair<String, String>>, d1: Long, d2: Long) {
        stop()
        running = true
        val g = ++gen
        val steps = ArrayList<Step>()
        apps.forEachIndexed { i, a -> steps.add(Step(1, a.first, a.second, d1, i + 1, apps.size)) }
        apps.forEachIndexed { i, a -> steps.add(Step(2, a.first, a.second, d2, i + 1, apps.size)) }
        log("Mulai: ${apps.size} app, 2 putaran")
        step(g, steps, 0)
    }

    fun stop() {
        if (running) log("Dihentikan")
        running = false
        gen++
        h.removeCallbacksAndMessages(null)
    }

    private fun step(g: Int, steps: List<Step>, i: Int) {
        if (g != gen || !running) return
        if (i >= steps.size) { running = false; log("Selesai"); return }
        val s = steps[i]
        val intent = packageManager.getLaunchIntentForPackage(s.pkg)
        if (intent == null) {
            log("Gagal buka ${s.pkg}")
            step(g, steps, i + 1)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(intent) } catch (e: Exception) { log("Error ${s.pkg}: ${e.message}") }
        log("Putaran ${s.round}: buka ${s.label} (${s.idx}/${s.n}) - ${s.pkg}")
        h.postDelayed({
            if (g == gen && running) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                h.postDelayed({ step(g, steps, i + 1) }, 1500)
            }
        }, s.delay)
    }
}
