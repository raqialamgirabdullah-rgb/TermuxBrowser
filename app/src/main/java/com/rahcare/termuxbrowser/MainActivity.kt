package com.rahcare.termuxbrowser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class Tab(val web: WebView) {
    var title = "নতুন ট্যাব"
    var url = ""
    val isHome get() = url.startsWith(Home.BASE)
}

class MainActivity : Activity() {
    companion object { const val REFRESH = "com.rahcare.termuxbrowser.REFRESH" }

    private val tabs = mutableListOf<Tab>()
    private var cur: Tab? = null
    private var desktop = false
    private lateinit var content: FrameLayout
    private lateinit var address: EditText
    private lateinit var countBox: TextView
    private lateinit var progress: ProgressBar
    private var overlay: View? = null

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            tabs.filter { it.isHome }.forEach {
                it.web.evaluateJavascript("var e=document.getElementById('jobs');if(e)e.innerHTML=${JSONObject.quote(Home.jobsHtml())}", null)
            }
            if (overlay != null) showSwitcher()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, r: Int = 20, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(2), stroke)
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        desktop = Store.prefs(this).getBoolean("desktop", false)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4)); setBackgroundColor(Color.WHITE)
        }
        fun icon(t: String, size: Float = 22f, click: () -> Unit) = TextView(this).apply {
            text = t; textSize = size; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#374151"))
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(44)); setOnClickListener { click() }
        }
        bar.addView(icon("⌂", 26f) { openHome() })
        address = EditText(this).apply {
            setSingleLine(); textSize = 14f; hint = "সার্চ বা ঠিকানা লিখুন"; background = bg(Color.parseColor("#F1F3F4"), 22)
            setPadding(dp(16), 0, dp(16), 0); imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI or android.text.InputType.TYPE_CLASS_TEXT
            setOnEditorActionListener { _, a, e ->
                if (a == EditorInfo.IME_ACTION_GO || (e != null && e.keyCode == KeyEvent.KEYCODE_ENTER)) { go(text.toString()); true } else false
            }
            setOnFocusChangeListener { v, f -> if (f) (v as EditText).post { v.selectAll() } }
        }
        bar.addView(address, LinearLayout.LayoutParams(0, dp(40), 1f))
        bar.addView(icon("＋", 24f) { newTab(null); openHome() })
        countBox = TextView(this).apply {
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#374151"))
            background = bg(Color.WHITE, 6, Color.parseColor("#374151"))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { setMargins(dp(8), 0, dp(8), 0) }
            setOnClickListener { if (overlay != null) hideSwitcher() else showSwitcher() }
        }
        bar.addView(countBox)
        val dots = icon("⋮", 24f) {}
        dots.setOnClickListener { menu(dots) }
        bar.addView(dots)
        root.addView(bar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        restore()
        if (tabs.isEmpty()) { newTab(null); openHome() }
        handleIntent(intent)
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); handleIntent(i) }
    private fun handleIntent(i: Intent?) {
        val u = i?.data?.toString() ?: return
        newTab(null); go(u)
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(rx, IntentFilter(REFRESH), Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(rx, IntentFilter(REFRESH))
        // অনুমতি/ইনস্টল বদলে থাকলে হোম পেজ নতুন করে আঁকি
        cur?.let { if (it.isHome) loadHome(it) }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(rx) } catch (_: Exception) {}
        save()
    }

    override fun onDestroy() { super.onDestroy(); tabs.forEach { it.web.destroy() } }

    // ---------- ট্যাব ----------
    private fun newTab(url: String?): Tab {
        val w = WebView(this)
        val t = Tab(w)
        w.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            builtInZoomControls = true; displayZoomControls = false
            useWideViewPort = true; loadWithOverviewMode = true
        }
        applyDesktop(t)
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                when (u.scheme) {
                    "http", "https" -> return false
                    "termux" -> {
                        // শুধু আমাদের নিজস্ব হোম পেজ থেকেই Termux কমান্ড চলবে; অন্য কোনো সাইট পারবে না
                        if (v.url?.startsWith(Home.BASE) == true) termuxAction(u, t)
                        return true
                    }
                    "tel", "mailto" -> try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                }
                return true
            }
            override fun onPageStarted(v: WebView, u: String, f: Bitmap?) { t.url = u; if (t === cur) updateBar() }
            override fun onPageFinished(v: WebView, u: String) {
                t.url = u; if (t.isHome) t.title = "Termux হোম" else if (!v.title.isNullOrBlank()) t.title = v.title!!
                if (t === cur) updateBar()
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (t === cur) { progress.progress = p; progress.visibility = if (p in 1..99) View.VISIBLE else View.GONE }
            }
            override fun onReceivedTitle(v: WebView, title: String?) { if (!t.isHome && !title.isNullOrBlank()) t.title = title }
            override fun onCreateWindow(v: WebView, d: Boolean, g: Boolean, m: Message): Boolean {
                val n = newTab(null)
                (m.obj as WebView.WebViewTransport).webView = n.web; m.sendToTarget(); return true
            }
            override fun onCloseWindow(v: WebView) { closeTab(t) }
        }
        w.setDownloadListener { u, ua, cd, mime, _ ->
            try {
                val rq = DownloadManager.Request(Uri.parse(u)).setMimeType(mime)
                    .addRequestHeader("User-Agent", ua)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(u, cd, mime))
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(rq)
                Toast.makeText(this, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this, "ডাউনলোড ব্যর্থ", Toast.LENGTH_SHORT).show() }
        }
        tabs.add(t); select(t)
        if (url != null) w.loadUrl(url)
        return t
    }

    private fun applyDesktop(t: Tab) {
        val ds = t.web.settings
        val ua = WebSettings.getDefaultUserAgent(this)
        ds.userAgentString = if (desktop) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36" else ua
    }

    private fun select(t: Tab) {
        cur = t; hideSwitcher()
        content.removeAllViews()
        (t.web.parent as? ViewGroup)?.removeView(t.web)
        content.addView(t.web, FrameLayout.LayoutParams(-1, -1))
        updateBar()
    }

    private fun closeTab(t: Tab) {
        val i = tabs.indexOf(t); if (i < 0) return
        tabs.remove(t); t.web.destroy()
        if (tabs.isEmpty()) { newTab(null); openHome(); return }
        if (cur === t) select(tabs[minOf(i, tabs.size - 1)]) else updateBar()
        if (overlay != null) showSwitcher()
    }

    private fun updateBar() {
        countBox.text = tabs.size.toString()
        val t = cur ?: return
        if (!address.hasFocus()) address.setText(if (t.isHome) "" else t.url)
        address.hint = if (t.isHome) "Termux হোম — সার্চ বা ঠিকানা লিখুন" else "সার্চ বা ঠিকানা লিখুন"
    }

    private fun loadHome(t: Tab) {
        t.url = Home.BASE; t.title = "Termux হোম"
        t.web.loadDataWithBaseURL(Home.BASE, Home.html(this), "text/html", "utf-8", null)
        if (t === cur) updateBar()
    }

    private fun openHome() { cur?.let { loadHome(it) } }

    private fun go(input: String) {
        val q = input.trim(); if (q.isEmpty()) return
        val u = when {
            q.startsWith("http://") || q.startsWith("https://") -> q
            !q.contains(" ") && (q.startsWith("localhost") || Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$").containsMatchIn(q)) -> "http://$q"
            !q.contains(" ") && q.contains(".") -> "https://$q"
            else -> "https://www.google.com/search?q=" + Uri.encode(q)
        }
        address.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(address.windowToken, 0)
        cur?.web?.loadUrl(u)
    }

    // ---------- Termux ----------
    private fun termuxAction(u: Uri, t: Tab) {
        val segs = u.pathSegments.ifEmpty { listOf(u.host ?: "") }
        val kind = u.host ?: ""
        val id = segs.lastOrNull() ?: ""
        when (kind) {
            "run" -> {
                val cmd = Store.all(this).firstOrNull { it.id == id } ?: return
                val err = Runner.run(this, cmd)
                if (err != null) AlertDialog.Builder(this).setTitle("চালানো যায়নি").setMessage(err).setPositiveButton("ঠিক আছে", null).show()
                else {
                    if (cmd.background) Toast.makeText(this, "ব্যাকগ্রাউন্ডে চলছে: ${cmd.title}", Toast.LENGTH_SHORT).show()
                    t.web.post { rx.onReceive(this, Intent(REFRESH)) }
                }
            }
            "edit" -> edit(Store.all(this).firstOrNull { it.id == id })
        }
    }

    private fun edit(cmd: Cmd?) {
        val list = Store.all(this)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val t = EditText(this).apply { hint = "বাটনের নাম"; setText(cmd?.title ?: "") }
        val s = EditText(this).apply { hint = "কমান্ড (bash)"; typeface = Typeface.MONOSPACE; setText(cmd?.script ?: ""); minLines = 3; gravity = Gravity.TOP }
        val b = CheckBox(this).apply { text = "Termux টার্মিনাল স্ক্রিনে দেখাও (ঐচ্ছিক — না দিলে ব্যাকগ্রাউন্ডে চলবে)"; isChecked = cmd?.background == false }
        l.addView(t); l.addView(s); l.addView(b)
        val d = AlertDialog.Builder(this).setTitle(if (cmd == null) "নতুন কমান্ড" else "কমান্ড সম্পাদনা").setView(l)
            .setPositiveButton("সেভ") { _, _ ->
                val title = t.text.toString().trim(); val script = s.text.toString().trim()
                if (title.isEmpty() || script.isEmpty()) return@setPositiveButton
                if (cmd == null) list.add(Cmd("c${System.currentTimeMillis()}", title, script, !b.isChecked))
                else list.firstOrNull { it.id == cmd.id }?.let { it.title = title; it.script = script; it.background = !b.isChecked }
                Store.save(this, list); refreshHomes()
            }.setNegativeButton("বাতিল", null)
        if (cmd != null) d.setNeutralButton("মুছুন") { _, _ -> list.removeAll { it.id == cmd.id }; Store.save(this, list); refreshHomes() }
        d.show()
    }

    private fun refreshHomes() { tabs.filter { it.isHome }.forEach { loadHome(it) } }

    // ---------- ট্যাব তালিকা ----------
    private fun hideSwitcher() { overlay?.let { content.removeView(it) }; overlay = null }

    private fun showSwitcher() {
        overlay?.let { content.removeView(it) }
        val sv = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#F3F4F6")); isClickable = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        sv.addView(col)
        fun head(t: String) = TextView(this).apply {
            text = t; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#374151")); setPadding(dp(2), dp(10), 0, dp(8))
        }
        col.addView(head("খোলা ট্যাব (${tabs.size})"))
        tabs.forEach { t ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; background = bg(if (t === cur) Color.parseColor("#DBEAFE") else Color.WHITE, 14)
                setPadding(dp(14), dp(10), dp(6), dp(10)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                setOnClickListener { select(t) }
            }
            val txt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            txt.addView(TextView(this).apply { text = t.title; textSize = 15f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setTextColor(Color.parseColor("#111827")) })
            txt.addView(TextView(this).apply {
                text = if (t.isHome) "Termux হোম" else t.url; textSize = 11.5f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setTextColor(Color.parseColor("#6B7280"))
            })
            row.addView(txt, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply { text = "✕"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#6B7280"))
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(40)); setOnClickListener { closeTab(t) } })
            col.addView(row)
        }
        col.addView(Button(this).apply { text = "＋ নতুন ট্যাব"; isAllCaps = false; setOnClickListener { newTab(null); openHome() } })
        col.addView(head("Termux-এ ব্যাকগ্রাউন্ডে কাজ (${Jobs.running()} চলছে)"))
        if (Jobs.list.isEmpty()) col.addView(TextView(this).apply { text = "এখনো কোনো কমান্ড চালানো হয়নি"; setTextColor(Color.parseColor("#6B7280")); textSize = 13f })
        Jobs.list.asReversed().forEach { j ->
            val c = when (j.status) { "running" -> "#F59E0B"; "ok" -> "#16A34A"; else -> "#DC2626" }
            val st = when (j.status) { "running" -> "চলছে…"; "ok" -> "সম্পন্ন"; else -> "ব্যর্থ" }
            col.addView(TextView(this).apply {
                text = "●  ${j.title} — $st"; textSize = 14f; setTextColor(Color.parseColor(c)); background = bg(Color.WHITE, 12)
                setPadding(dp(14), dp(12), dp(14), dp(12)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle(j.title).setMessage(if (j.out.isBlank()) st else j.out)
                        .setPositiveButton("ঠিক আছে", null).show()
                }
            })
        }
        overlay = sv
        content.addView(sv, FrameLayout.LayoutParams(-1, -1))
    }

    // ---------- মেনু ----------
    private fun menu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, "নতুন ট্যাব")
        m.menu.add(0, 2, 1, "রিলোড")
        m.menu.add(0, 3, 2, "ডেস্কটপ সাইট").apply { isCheckable = true; isChecked = desktop }
        m.menu.add(0, 4, 3, "Termux কমান্ড ম্যানেজ")
        m.menu.add(0, 5, 4, "Termux সেটআপ ও অবস্থা")
        m.menu.add(0, 6, 5, "অ্যাপ অনুমতি (সেটিংস)")
        m.menu.add(0, 7, 6, "ব্রাউজিং ডেটা মুছুন")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> { newTab(null); openHome() }
                2 -> cur?.let { t -> if (t.isHome) loadHome(t) else t.web.reload() }
                3 -> { desktop = !desktop; Store.prefs(this).edit().putBoolean("desktop", desktop).apply()
                    tabs.forEach { t -> applyDesktop(t) }; cur?.let { t -> if (!t.isHome) t.web.reload() } }
                4 -> manage()
                5 -> status()
                6 -> startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                7 -> { WebStorage.getInstance().deleteAllData(); CookieManager.getInstance().removeAllCookies(null)
                    tabs.forEach { t -> t.web.clearCache(true); t.web.clearHistory() }; Toast.makeText(this, "মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show() }
            }
            true
        }
        m.show()
    }

    private fun manage() {
        val list = Store.all(this)
        val names = list.map { it.title }.toTypedArray()
        AlertDialog.Builder(this).setTitle("কমান্ড (চাপলে সম্পাদনা)")
            .setItems(names) { _, i -> edit(list[i]) }
            .setPositiveButton("＋ নতুন") { _, _ -> edit(null) }.setNegativeButton("বন্ধ", null).show()
    }

    private fun status() {
        val msg = buildString {
            append(if (Runner.installed(this@MainActivity)) "✓ Termux ইনস্টল আছে\n" else "✗ Termux ইনস্টল নেই (F-Droid থেকে নিন)\n")
            append(if (Runner.hasPermission(this@MainActivity)) "✓ RUN_COMMAND অনুমতি আছে\n\n" else "✗ RUN_COMMAND অনুমতি নেই (⋮ › অ্যাপ অনুমতি)\n\n")
            append("Termux-এ একবার চালান:\nmkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties\nতারপর Termux রিস্টার্ট করুন।\n\nTermux-এ ওয়েব সার্ভার চালালে (যেমন code-server) এখানে localhost:পোর্ট লিখে ব্রাউজারেই ব্যবহার করুন।")
        }
        AlertDialog.Builder(this).setTitle("Termux অবস্থা").setMessage(msg).setPositiveButton("ঠিক আছে", null).show()
    }

    // ---------- সেশন ----------
    private fun save() {
        val a = JSONArray()
        tabs.forEach { a.put(if (it.isHome || it.url.isBlank()) "home" else it.url) }
        Store.prefs(this).edit().putString("tabs", a.toString()).putInt("cur", tabs.indexOf(cur)).apply()
    }

    private fun restore() {
        try {
            val a = JSONArray(Store.prefs(this).getString("tabs", "[]"))
            for (i in 0 until a.length()) {
                val u = a.getString(i)
                val t = newTab(null)
                if (u == "home") loadHome(t) else t.web.loadUrl(u)
            }
            val c = Store.prefs(this).getInt("cur", 0)
            if (tabs.isNotEmpty()) select(tabs[c.coerceIn(0, tabs.size - 1)])
        } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val t = cur
        when {
            overlay != null -> hideSwitcher()
            t != null && t.web.canGoBack() -> t.web.goBack()
            t != null && !t.isHome -> loadHome(t)
            tabs.size > 1 && t != null -> closeTab(t)
            else -> super.onBackPressed()
        }
    }
}
