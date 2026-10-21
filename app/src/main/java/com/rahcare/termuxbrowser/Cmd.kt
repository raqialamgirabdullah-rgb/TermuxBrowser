package com.rahcare.termuxbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** একটি বাটন = একটি Termux কমান্ড। background=true হলে Termux খোলে না, ব্রাউজারেই থাকা যায় */
data class Cmd(val id: String, var title: String, var script: String, var background: Boolean = true)

/** ব্যাকগ্রাউন্ডে চলা কাজ */
data class Job(val key: String, val title: String, var status: String, var out: String, val time: Long) {
    var end = 0L          // শেষ হওয়ার সময় (০ = এখনো চলছে)
    var script = ""
    var pct = -1          // আউটপুটে পাওয়া শেষ "NN%" (না পেলে -১)
    @Volatile var lastPush = 0L

    private val ansi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B\\][^\u0007]*\u0007")
    private val percent = Regex("(\\d{1,3})\\s?%")

    /** পর্দায় দেখানোর লেখা: রঙের কোড বাদ, \r দিয়ে বদলানো লাইনে শুধু শেষ অংশ */
    fun text(): String {
        var s = if (out.length > 20000) out.takeLast(20000) else out
        s = ansi.replace(s, "").replace("\r\n", "\n")
        return s.split('\n').joinToString("\n") { it.substringAfterLast('\r') }
    }

    fun refreshPct() {
        val m = percent.findAll(text().takeLast(400)).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        pct = if (m != null && m in 0..100) m else -1
    }

    fun elapsed() = ((if (end > 0) end else System.currentTimeMillis()) - time) / 1000
    fun lastLine() = text().lines().lastOrNull { it.isNotBlank() }?.trim()?.take(120) ?: ""
}

object Jobs {
    val list = mutableListOf<Job>()
    fun start(title: String, script: String = ""): Job {
        val j = Job("j${System.nanoTime()}", title, "running", "", System.currentTimeMillis())
        j.script = script
        list.add(j); if (list.size > 30) list.removeAt(0); return j
    }
    fun find(key: String) = list.firstOrNull { it.key == key }
    fun running() = list.count { it.status == "running" }
}

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("cmds", Context.MODE_PRIVATE)

    private val defaults = listOf(
        Cmd("d1", "প্যাকেজ আপডেট", "pkg update -y && pkg upgrade -y"),
        Cmd("d2", "স্টোরেজ অনুমতি", "termux-setup-storage", false),
        Cmd("d3", "Python ইনস্টল", "pkg install -y python"),
        Cmd("d4", "Git ইনস্টল", "pkg install -y git"),
        Cmd("d5", "Node.js ইনস্টল", "pkg install -y nodejs"),
        Cmd("d6", "SSH সার্ভার চালু", "pkg install -y openssh && sshd && echo 'sshd চালু, পোর্ট 8022' && whoami"),
        Cmd("d7", "ডিস্ক ব্যবহার", "df -h \$HOME"),
        Cmd("d8", "আইপি দেখুন", "(command -v ifconfig >/dev/null || pkg install -y net-tools) && ifconfig 2>/dev/null | grep -E \"inet |wlan|rmnet\" || /system/bin/ip -4 addr 2>/dev/null | grep inet"),
        Cmd("d9", "সিস্টেম তথ্য", "uname -a && echo && free -h 2>/dev/null | head -3"),
        Cmd("d10", "🖥 কম্পিউটার মোড (হালকা ডেস্কটপ)",
            "pkg install -y x11-repo && pkg install -y termux-x11-nightly openbox tint2 xterm pcmanfm && " +
            "pkill -f termux-x11 2>/dev/null; (termux-x11 :0 >/dev/null 2>&1 &) ; sleep 3; " +
            "am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity >/dev/null 2>&1; " +
            "export DISPLAY=:0; (tint2 >/dev/null 2>&1 &); exec openbox-session", false),
        // ওয়েব-টার্মিনাল: Termux-এ ttyd চালু হয়, তারপর অ্যাপ নতুন ট্যাবে খোলে। শুধু 127.0.0.1-এ, পাসওয়ার্ডসহ (@@WEBPW@@ অ্যাপ বসায়)
        Cmd("d12", "🌐 ওয়েব-টার্মিনাল (ট্যাবে পূর্ণ টার্মিনাল)",
            "pkg install -y ttyd && { pkill -x ttyd 2>/dev/null; nohup setsid ttyd -W -i 127.0.0.1 -p 7681 -c tb:@@WEBPW@@ bash -l >/dev/null 2>&1 & sleep 2; echo 'ওয়েব-টার্মিনাল চালু — নতুন ট্যাব খুলছে'; }"),
        Cmd("d11", "কম্পিউটার মোড বন্ধ", "pkill -f openbox; pkill -f tint2; pkill -f termux-x11; echo বন্ধ হয়েছে"),
    )

    fun all(c: Context): MutableList<Cmd> {
        val raw = sp(c).getString("list", null) ?: return defaults.map { it.copy() }.toMutableList()
        val out = mutableListOf<Cmd>()
        val seen = (sp(c).getString("seen", "") ?: "").split(",").toSet()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Cmd(o.getString("id"), o.getString("t"), o.getString("s"), o.optBoolean("b", true)))
            }
        } catch (_: Exception) { return defaults.map { it.copy() }.toMutableList() }
        var changed = false
        // আগের সংস্করণে সব কমান্ড Termux স্ক্রিনে চলত — এখন ডিফল্ট ব্যাকগ্রাউন্ড
        if (sp(c).getInt("ver", 1) < 2) { out.forEach { it.background = true }; changed = true }
        // স্টোরেজ অনুমতি ও কম্পিউটার মোডে Termux-এর আসল পর্দা লাগে (Android-এর অনুমতি-পর্দা/ডেস্কটপ), ব্যাকগ্রাউন্ডে চললে কিছুই হয় না
        if (sp(c).getInt("ver", 1) < 3) { out.filter { it.id == "d2" || it.id == "d10" }.forEach { it.background = false }; changed = true }
        // পুরনো "ip" কমান্ড Termux-এ নেই (ip: command not found) — নতুনটা দিয়ে বদলাই
        if (sp(c).getInt("ver", 1) < 4) { out.filter { it.id == "d8" && it.script == "ip -4 addr | grep inet" }.forEach { it.script = defaults.first { d -> d.id == "d8" }.script }; changed = true }
        for (d in defaults) if (d.id !in seen && out.none { it.id == d.id }) { out.add(d.copy()); changed = true }
        if (changed) save(c, out)
        return out
    }

    fun save(c: Context, list: List<Cmd>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("s", it.script).put("b", it.background)) }
        sp(c).edit().putString("list", a.toString()).putInt("ver", 4)
            .putString("seen", defaults.joinToString(",") { it.id }).apply()
    }

    fun prefs(c: Context) = c.getSharedPreferences("browser", Context.MODE_PRIVATE)
}
