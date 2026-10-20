package com.rahcare.termuxbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    companion object { const val REFRESH = "com.rahcare.termuxbrowser.REFRESH" }

    private lateinit var list: MutableList<Cmd>
    private lateinit var box: LinearLayout
    private lateinit var status: TextView
    private lateinit var out: TextView
    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { out.text = Store.lastOut(this@MainActivity); out.visibility = if (out.text.isEmpty()) View.GONE else View.VISIBLE }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, r: Int = 14) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = Color.parseColor("#111827")
        list = Store.all(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F3F4F6")) }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#111827")); setPadding(dp(18), dp(18), dp(18), dp(14))
        }
        head.addView(TextView(this).apply { text = "Termux Browser"; setTextColor(Color.WHITE); textSize = 22f; typeface = Typeface.DEFAULT_BOLD })
        status = TextView(this).apply { setTextColor(Color.parseColor("#9CA3AF")); textSize = 13f; setPadding(0, dp(4), 0, 0) }
        head.addView(status)
        root.addView(head)

        val sv = ScrollView(this)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        sv.addView(box)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        out = TextView(this).apply {
            setTextColor(Color.parseColor("#D1FAE5")); typeface = Typeface.MONOSPACE; textSize = 12f
            background = bg(Color.parseColor("#111827")); setPadding(dp(12), dp(10), dp(12), dp(10)); setTextIsSelectable(true)
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(rx, IntentFilter(REFRESH), Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(rx, IntentFilter(REFRESH))
        refreshStatus()
    }
    override fun onPause() { super.onPause(); try { unregisterReceiver(rx) } catch (_: Exception) {} }

    private fun refreshStatus() {
        status.text = when {
            !Runner.installed(this) -> "⚠ Termux ইনস্টল নেই"
            !Runner.hasPermission(this) -> "⚠ RUN_COMMAND অনুমতি দরকার (নিচের সেটআপ দেখুন)"
            else -> "✓ Termux প্রস্তুত"
        }
    }

    private fun render() {
        box.removeAllViews()
        refreshStatus()
        if (!Runner.installed(this) || !Runner.hasPermission(this)) box.addView(setupCard())
        list.forEach { box.addView(row(it)) }
        box.addView(Button(this).apply {
            text = "＋ নতুন কমান্ড"; isAllCaps = false; setOnClickListener { edit(null) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        out.text = Store.lastOut(this)
        out.visibility = if (out.text.isEmpty()) View.GONE else View.VISIBLE
        (out.parent as? LinearLayout)?.removeView(out)
        box.addView(out, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun setupCard(): View {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg(Color.parseColor("#FEF3C7")); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        c.addView(TextView(this).apply {
            setTextColor(Color.parseColor("#78350F")); textSize = 13.5f
            text = "সেটআপ (একবারই):\n১) Termux (F-Droid) ইনস্টল করুন\n২) Termux-এ চালান:\n   mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties\n৩) Termux বন্ধ করে আবার খুলুন\n৪) এই অ্যাপের অনুমতি দিন: সেটিংস › অ্যাপ › Termux Browser › অনুমতি › \"Run commands in Termux environment\""
        })
        c.addView(Button(this).apply {
            text = "অ্যাপ সেটিংস খুলুন"; isAllCaps = false
            setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        })
        c.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        return c
    }

    private fun row(cmd: Cmd): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg(Color.WHITE); setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
        card.addView(TextView(this).apply { text = cmd.title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#111827")) })
        card.addView(TextView(this).apply {
            text = cmd.script; typeface = Typeface.MONOSPACE; textSize = 11.5f; maxLines = 2; setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(3), 0, dp(8))
        })
        val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        line.addView(Button(this).apply {
            text = if (cmd.background) "▶ চালান (ব্যাকগ্রাউন্ড)" else "▶ চালান (Termux-এ)"; isAllCaps = false
            setOnClickListener {
                val err = Runner.run(this@MainActivity, cmd)
                if (err != null) AlertDialog.Builder(this@MainActivity).setTitle("চালানো যায়নি").setMessage(err).setPositiveButton("ঠিক আছে", null).show()
                else if (cmd.background) Toast.makeText(this@MainActivity, "চলছে… আউটপুট নিচে আসবে", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(Button(this).apply { text = "✎"; setOnClickListener { edit(cmd) } })
        card.addView(line)
        return card
    }

    private fun edit(cmd: Cmd?) {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val t = EditText(this).apply { hint = "বাটনের নাম"; setText(cmd?.title ?: "") }
        val s = EditText(this).apply { hint = "কমান্ড (bash)"; typeface = Typeface.MONOSPACE; setText(cmd?.script ?: ""); minLines = 3; gravity = Gravity.TOP }
        val b = CheckBox(this).apply { text = "ব্যাকগ্রাউন্ডে চালান ও আউটপুট দেখান"; isChecked = cmd?.background ?: false }
        l.addView(t); l.addView(s); l.addView(b)
        val d = AlertDialog.Builder(this).setTitle(if (cmd == null) "নতুন কমান্ড" else "কমান্ড সম্পাদনা").setView(l)
            .setPositiveButton("সেভ") { _, _ ->
                val title = t.text.toString().trim(); val script = s.text.toString().trim()
                if (title.isEmpty() || script.isEmpty()) return@setPositiveButton
                if (cmd == null) list.add(Cmd("c${System.currentTimeMillis()}", title, script, b.isChecked))
                else { cmd.title = title; cmd.script = script; cmd.background = b.isChecked }
                Store.save(this, list); render()
            }.setNegativeButton("বাতিল", null)
        if (cmd != null) d.setNeutralButton("মুছুন") { _, _ -> list.remove(cmd); Store.save(this, list); render() }
        d.show()
    }
}
