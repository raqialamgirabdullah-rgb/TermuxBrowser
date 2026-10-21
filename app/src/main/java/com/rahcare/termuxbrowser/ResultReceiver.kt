package com.rahcare.termuxbrowser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** ব্যাকগ্রাউন্ড কমান্ডের শেষ ফলাফল ধরে Jobs-এ রাখে (লাইভ আউটপুট থাকলে সেটা মুছে না, শুধু শেষ অবস্থা মেলায়) */
class ResultReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val job = Jobs.find(i.getStringExtra("job") ?: return) ?: return
        val live = job.out.isNotBlank()
        val b = i.getBundleExtra("result")
        if (b == null) {
            if (job.status == "running") { job.status = "fail"; job.end = System.currentTimeMillis() }
            if (!live) job.out = "(আউটপুট নেই)"
        } else {
            val out = b.getString("stdout", "") ?: ""
            val err = b.getString("stderr", "") ?: ""
            val code = b.getInt("exitCode", -1)
            val em = b.getString("errmsg", "") ?: ""
            if (job.status == "running") {
                job.status = if (code == 0 && em.isBlank()) "ok" else "fail"
                job.end = System.currentTimeMillis()
            }
            val extra = buildString {
                if (!live) append(out.takeLast(3000))
                if (err.isNotBlank()) append("\n").append(err.takeLast(1500))
                if (em.isNotBlank()) append("\n[ত্রুটি] ").append(em)
                if (!live) append("\n(exit ").append(code).append(")")
            }
            job.out = (job.out + extra).trim()
        }
        job.refreshPct()
        c.sendBroadcast(Intent(MainActivity.REFRESH).setPackage(c.packageName))
    }
}
