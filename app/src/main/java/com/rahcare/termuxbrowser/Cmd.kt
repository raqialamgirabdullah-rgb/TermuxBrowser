package com.rahcare.termuxbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** একটি বাটন = একটি Termux কমান্ড। background=true হলে Termux খোলে না, ব্রাউজারেই থাকা যায় */
data class Cmd(val id: String, var title: String, var script: String, var background: Boolean = true)

/** ব্যাকগ্রাউন্ডে চলা কাজ */
data class Job(val key: String, val title: String, var status: String, var out: String, val time: Long)

object Jobs {
    val list = mutableListOf<Job>()
    fun start(title: String): Job {
        val j = Job("j${System.nanoTime()}", title, "running", "", System.currentTimeMillis())
        list.add(j); if (list.size > 30) list.removeAt(0); return j
    }
    fun find(key: String) = list.firstOrNull { it.key == key }
    fun running() = list.count { it.status == "running" }
}

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("cmds", Context.MODE_PRIVATE)

    private val defaults = listOf(
        Cmd("d1", "প্যাকেজ আপডেট", "pkg update -y && pkg upgrade -y"),
        Cmd("d2", "স্টোরেজ অনুমতি", "termux-setup-storage"),
        Cmd("d3", "Python ইনস্টল", "pkg install -y python"),
        Cmd("d4", "Git ইনস্টল", "pkg install -y git"),
        Cmd("d5", "Node.js ইনস্টল", "pkg install -y nodejs"),
        Cmd("d6", "SSH সার্ভার চালু", "pkg install -y openssh && sshd && echo 'sshd চালু, পোর্ট 8022' && whoami"),
        Cmd("d7", "ডিস্ক ব্যবহার", "df -h \$HOME"),
        Cmd("d8", "আইপি দেখুন", "ip -4 addr | grep inet"),
        Cmd("d9", "সিস্টেম তথ্য", "uname -a && echo && free -h 2>/dev/null | head -3"),
        Cmd("d10", "🖥 কম্পিউটার মোড (হালকা ডেস্কটপ)",
            "pkg install -y x11-repo && pkg install -y termux-x11-nightly openbox tint2 xterm pcmanfm && " +
            "pkill -f termux-x11 2>/dev/null; (termux-x11 :0 >/dev/null 2>&1 &) ; sleep 3; " +
            "am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity >/dev/null 2>&1; " +
            "export DISPLAY=:0; (tint2 >/dev/null 2>&1 &); exec openbox-session"),
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
        for (d in defaults) if (d.id !in seen && out.none { it.id == d.id }) { out.add(d.copy()); changed = true }
        if (changed) save(c, out)
        return out
    }

    fun save(c: Context, list: List<Cmd>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("s", it.script).put("b", it.background)) }
        sp(c).edit().putString("list", a.toString()).putInt("ver", 2)
            .putString("seen", defaults.joinToString(",") { it.id }).apply()
    }

    fun prefs(c: Context) = c.getSharedPreferences("browser", Context.MODE_PRIVATE)
}
