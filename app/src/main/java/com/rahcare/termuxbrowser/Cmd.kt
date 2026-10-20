package com.rahcare.termuxbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** একটি বাটন = একটি Termux কমান্ড */
data class Cmd(val id: String, var title: String, var script: String, var background: Boolean = false)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("cmds", Context.MODE_PRIVATE)

    private val defaults = listOf(
        Cmd("d1", "প্যাকেজ আপডেট", "pkg update -y && pkg upgrade -y"),
        Cmd("d2", "স্টোরেজ অনুমতি", "termux-setup-storage"),
        Cmd("d3", "Python ইনস্টল", "pkg install -y python"),
        Cmd("d4", "Git ইনস্টল", "pkg install -y git"),
        Cmd("d5", "Node.js ইনস্টল", "pkg install -y nodejs"),
        Cmd("d6", "SSH সার্ভার চালু", "pkg install -y openssh && sshd && echo 'sshd চালু, পোর্ট 8022' && whoami"),
        Cmd("d7", "ডিস্ক ব্যবহার", "df -h \$HOME", true),
        Cmd("d8", "আইপি দেখুন", "ip -4 addr | grep inet", true),
        Cmd("d9", "সিস্টেম তথ্য", "uname -a && echo && free -h 2>/dev/null | head -3", true),
    )

    fun all(c: Context): MutableList<Cmd> {
        val raw = sp(c).getString("list", null) ?: return defaults.toMutableList()
        val out = mutableListOf<Cmd>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Cmd(o.getString("id"), o.getString("t"), o.getString("s"), o.optBoolean("b")))
            }
        } catch (_: Exception) { return defaults.toMutableList() }
        return out
    }

    fun save(c: Context, list: List<Cmd>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("s", it.script).put("b", it.background)) }
        sp(c).edit().putString("list", a.toString()).apply()
    }

    fun lastOut(c: Context): String = sp(c).getString("out", "") ?: ""
    fun setOut(c: Context, s: String) = sp(c).edit().putString("out", s).apply()
}
