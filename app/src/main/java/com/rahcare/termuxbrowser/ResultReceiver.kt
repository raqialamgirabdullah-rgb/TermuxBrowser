package com.rahcare.termuxbrowser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** ব্যাকগ্রাউন্ড কমান্ডের ফলাফল ধরে Jobs-এ রাখে */
class ResultReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val job = Jobs.find(i.getStringExtra("job") ?: return) ?: return
        val b = i.getBundleExtra("result")
        if (b == null) { job.status = "fail"; job.out = "(আউটপুট নেই)" }
        else {
            val out = b.getString("stdout", "") ?: ""
            val err = b.getString("stderr", "") ?: ""
            val code = b.getInt("exitCode", -1)
            val em = b.getString("errmsg", "") ?: ""
            job.status = if (code == 0 && em.isBlank()) "ok" else "fail"
            job.out = buildString {
                append(out.takeLast(3000))
                if (err.isNotBlank()) append("\n").append(err.takeLast(1500))
                if (em.isNotBlank()) append("\n[ত্রুটি] ").append(em)
                append("\n(exit ").append(code).append(")")
            }.trim()
        }
        c.sendBroadcast(Intent(MainActivity.REFRESH).setPackage(c.packageName))
    }
}
