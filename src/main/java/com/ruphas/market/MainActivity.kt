package com.ruphas.market

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {

    private class App(val label: String, val pkg: String)

    private val apps = ArrayList<App>()
    private val checks = ArrayList<CheckBox>()
    private lateinit var listBox: LinearLayout
    private lateinit var filter: EditText
    private lateinit var d1: EditText
    private lateinit var d2: EditText
    private lateinit var status: TextView
    private lateinit var logView: TextView

    private fun btn(t: String, f: () -> Unit) =
        Button(this).apply { text = t; setOnClickListener { f() } }

    private fun field(hint: String, v: String, num: Boolean) = EditText(this).apply {
        this.hint = hint
        setText(v)
        if (num) inputType = InputType.TYPE_CLASS_NUMBER
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }
        filter = field("Filter nama app", "NEXT", false)
        d1 = field("Detik putaran 1", "10", true)
        d2 = field("Detik putaran 2", "30", true)
        status = TextView(this)
        logView = TextView(this)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(TextView(this).apply { text = "Ruphas Market"; textSize = 24f })
        root.addView(filter)
        root.addView(btn("Muat daftar app") { loadApps() })
        root.addView(listBox)
        root.addView(TextView(this).apply { text = "Putaran 1: tunggu (detik) sampai loading setengah, lalu Home" })
        root.addView(d1)
        root.addView(TextView(this).apply { text = "Putaran 2: tunggu (detik) sampai ketemu Layla, lalu Home" })
        root.addView(d2)
        root.addView(btn("1. Aktifkan layanan Aksesibilitas") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(btn("2. MULAI") { startRun() })
        root.addView(btn("HENTIKAN") { RunnerService.instance?.stop() })
        root.addView(status)
        root.addView(logView)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun loadApps() {
        val q = filter.text.toString().trim()
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = packageManager.queryIntentActivities(i, 0)
            .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName && (q.isEmpty() || it.label.contains(q, true)) }
            .distinctBy { it.pkg }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.pkg }))
        apps.clear(); apps.addAll(found)
        checks.clear(); listBox.removeAllViews()
        for (a in apps) {
            val c = CheckBox(this).apply { text = "${a.label}\n${a.pkg}"; isChecked = true }
            checks.add(c); listBox.addView(c)
        }
        Toast.makeText(this, "${apps.size} app ditemukan", Toast.LENGTH_SHORT).show()
    }

    private fun startRun() {
        val s = RunnerService.instance
        if (s == null) {
            Toast.makeText(this, "Aktifkan layanan Ruphas Market di Aksesibilitas dulu", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val sel = checks.indices.filter { checks[it].isChecked }.map { apps[it].pkg to apps[it].label }
        if (sel.isEmpty()) {
            Toast.makeText(this, "Belum ada app dipilih", Toast.LENGTH_SHORT).show()
            return
        }
        val a = (d1.text.toString().toLongOrNull() ?: 10L) * 1000
        val b = (d2.text.toString().toLongOrNull() ?: 30L) * 1000
        s.start(sel, a, b)
    }

    private fun refresh() {
        val s = RunnerService.instance
        status.text = when {
            s == null -> "Status: layanan Aksesibilitas belum aktif"
            s.isRunning() -> "Status: berjalan"
            else -> "Status: siap"
        }
        logView.text = RunnerService.logs.joinToString("\n")
    }

    override fun onResume() {
        super.onResume()
        RunnerService.onChange = { refresh() }
        if (apps.isEmpty()) loadApps()
        refresh()
    }

    override fun onPause() {
        RunnerService.onChange = null
        super.onPause()
    }
}
