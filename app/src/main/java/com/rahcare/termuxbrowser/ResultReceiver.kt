package com.rahcare.termuxbrowser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** ব্যাকগ্রাউন্ড কমান্ডের আউটপুট ধরে রাখে */
class ResultReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val title = i.getStringExtra("title") ?: "কমান্ড"
        val b = i.getBundleExtra("result")
        val txt = if (b == null) "(আউটপুট নেই)" else {
            val out = b.getString("stdout", "")
            val err = b.getString("stderr", "")
            val code = b.getInt("exitCode", -1)
            val em = b.getString("errmsg", "")
            buildString {
                append(out)
                if (!err.isNullOrBlank()) append("\n[stderr]\n").append(err)
                if (!em.isNullOrBlank()) append("\n[ত্রুটি] ").append(em)
                append("\n(exit $code)")
            }
        }
        Store.setOut(c, "▶ $title\n$txt")
        c.sendBroadcast(Intent(MainActivity.REFRESH).setPackage(c.packageName))
    }
}
