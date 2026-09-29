package com.ruphas.market

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.setBackgroundColor(0xFF1A1114.toInt())
        w.addJavascriptInterface(Bridge(), "App")
        w.loadUrl("file:///android_asset/index.html")
        setContentView(w)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun apps(): String {
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val out = JSONArray()
            packageManager.queryIntentActivities(q, 0)
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
                .forEach {
                    out.put(JSONObject()
                        .put("n", it.loadLabel(packageManager).toString())
                        .put("p", it.activityInfo.packageName))
                }
            return out.toString()
        }

        @JavascriptInterface
        fun start(pkgs: String, g1: Int, g2: Int, back: Boolean) {
            runOnUiThread {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    overlay()
                } else {
                    val arr = JSONArray(pkgs)
                    val list = Array(arr.length()) { arr.getString(it) }
                    val svc = Intent(this@MainActivity, RunnerService::class.java)
                        .putExtra("pkgs", list)
                        .putExtra("g1", g1 * 1000L)
                        .putExtra("g2", g2 * 1000L)
                        .putExtra("back", back)
                    startForegroundService(svc)
                }
            }
        }

        @JavascriptInterface
        fun stop() {
            runOnUiThread {
                startService(Intent(this@MainActivity, RunnerService::class.java).setAction("STOP"))
            }
        }

        @JavascriptInterface
        fun status(): String {
            val o = JSONObject()
                .put("r", RunnerService.running)
                .put("d", RunnerService.done)
                .put("t", RunnerService.total)
                .put("o", Settings.canDrawOverlays(this@MainActivity))
            synchronized(RunnerService.logs) { o.put("l", JSONArray(RunnerService.logs)) }
            return o.toString()
        }

        @JavascriptInterface
        fun overlay() {
            runOnUiThread {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }

        @JavascriptInterface
        fun battery() {
            runOnUiThread {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}
